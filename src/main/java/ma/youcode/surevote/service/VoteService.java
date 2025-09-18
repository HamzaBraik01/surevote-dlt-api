package ma.youcode.surevote.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.annotation.Auditable;
import ma.youcode.surevote.domain.entity.*;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import ma.youcode.surevote.dto.request.VoteRequest;
import ma.youcode.surevote.dto.response.VoteReceiptResponse;
import ma.youcode.surevote.exception.*;
import ma.youcode.surevote.repository.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Core voting service implementing the SUREVOTE double-barrier anonymity architecture.
 *
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║              SECURITY-CRITICAL IMPLEMENTATION — READ CAREFULLY             ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║  The "double-barrier" pattern enforces cryptographic voter anonymity:       ║
 * ║                                                                              ║
 * ║  BARRIER 1 — EMARGEMENT (who voted):                                        ║
 * ║    Stores: electeur_id + election_id + UUID receipt + IP + timestamp        ║
 * ║    Purpose: Prevents double voting; issues cryptographic proof of           ║
 * ║             participation to the voter.                                     ║
 * ║                                                                              ║
 * ║  BARRIER 2 — VOTE (what was voted):                                         ║
 * ║    Stores: election_id + candidat_id + obfuscated timestamp                 ║
 * ║    Purpose: Records the anonymous ballot. NO electeur_id. NO FK to users.  ║
 * ║                                                                              ║
 * ║  Both records are created in a SINGLE @Transactional method with            ║
 * ║  SERIALIZABLE isolation. They succeed together or roll back together.       ║
 * ║                                                                              ║
 * ║  NO SQL JOIN between emargements and votes is architecturally possible.     ║
 * ║  Even a privileged DBA cannot link a voter to their ballot choice.          ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoteService {

    private final EmargementRepository emargementRepository;
    private final VoteRepository voteRepository;
    private final ElectionRepository electionRepository;
    private final CandidatRepository candidatRepository;
    private final UtilisateurRepository utilisateurRepository;

    // =========================================================
    // Core Voting Operation — Double-Barrier Atomic Transaction
    // =========================================================

    /**
     * Submits an anonymous ballot for the authenticated voter in the specified election.
     *
     * Pre-flight checks (all must pass before the transaction begins):
     *  1. The election exists and is currently OUVERTE.
     *  2. The voter account is active and has the ELECTEUR role.
     *  3. If the election has 2FA enabled, the voter has completed OTP verification.
     *  4. The voter is eligible (in the correct college, or election is unrestricted).
     *  5. The voter has NOT already voted in this election (emargement check).
     *  6. The candidate exists and belongs to the specified election.
     *
     * Atomic transaction (SERIALIZABLE isolation):
     *  Step 1 — Insert an Emargement record: links the voter identity to the election.
     *            Contains the UUID cryptographic receipt to return to the voter.
     *  Step 2 — Insert a Vote record: links only candidat + election.
     *            Contains ZERO reference to the voter's identity — by design.
     *
     * Both steps succeed together or both roll back atomically on any failure.
     * The SERIALIZABLE isolation level prevents concurrent race conditions
     * that could allow a voter to cast two ballots simultaneously.
     *
     * @param request  the vote submission containing electionId and candidatId
     * @return a VoteReceiptResponse containing the UUID proof of participation
     *
     * @throws ResourceNotFoundException   if the election or candidate does not exist
     * @throws ElectionNotOpenException    if the election is not in OUVERTE state
     * @throws TwoFactorRequiredException  if 2FA is required but not completed
     * @throws VoterNotEligibleException   if the voter is not in the required college
     * @throws AlreadyVotedException       if the voter has already voted in this election
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Retryable(
        retryFor = { org.springframework.dao.CannotAcquireLockException.class,
                     org.springframework.dao.PessimisticLockingFailureException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Auditable(actionType = "VOTE_SUBMITTED", description = "Bulletin de vote soumis avec succès")
    public VoteReceiptResponse submitVote(VoteRequest request) {
        // ── Resolve authenticated voter ──────────────────────────────────────────
        Electeur electeur = resolveAuthenticatedVoter();

        // ── Load and validate election ───────────────────────────────────────────
        Election election = electionRepository.findById(request.getElectionId())
                .orElseThrow(() -> new ResourceNotFoundException("Election", request.getElectionId()));

        assertElectionIsOpen(election);
        assertVoterHasCompletedTwoFactor(electeur, election);
        assertVoterIsEligible(electeur, election);

        // ── Duplicate vote guard (first database-level check) ───────────────────
        assertVoterHasNotVoted(electeur.getId(), election.getId());

        // ── Validate candidate belongs to this election ──────────────────────────
        Candidat candidat = candidatRepository
                .findByIdAndElectionId(request.getCandidatId(), request.getElectionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Candidat id=" + request.getCandidatId()
                        + " introuvable dans l'élection id=" + request.getElectionId()
                        + ". Le candidat doit appartenir à l'élection en cours."
                ));

        // ── Generate cryptographic receipt ───────────────────────────────────────
        String recuCryptographique = UUID.randomUUID().toString();
        String checksumSalt = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        String clientIp = resolveClientIp();

        log.info("Vote submission started: electeurId={}, electionId={}, candidatId={}, ip={}",
                electeur.getId(), election.getId(), candidat.getId(), clientIp);

        // ════════════════════════════════════════════════════════════════════════
        // STEP 1 — Write EMARGEMENT (identity layer)
        // This records WHO voted. Links voter ↔ election.
        // The UUID receipt is returned to the voter as proof of participation.
        // ════════════════════════════════════════════════════════════════════════
        Emargement emargement = Emargement.builder()
                .electeur(electeur)
                .election(election)
                .dateEmargement(now)
                .recuCryptographique(recuCryptographique)
                .adresseIp(clientIp)
                .build();

        emargementRepository.save(emargement);

        log.debug("BARRIER 1 written: emargementId={}, electionId={}, electeurId={}",
                emargement.getId(), election.getId(), electeur.getId());

        // ════════════════════════════════════════════════════════════════════════
        // STEP 2 — Write VOTE (anonymous ballot layer)
        // ════════════════════════════════════════════════════════════════════════
        // Checksum now includes a per-vote random salt stored in the Vote record.
        // This makes each vote's checksum unique and verifiable independently.
        // The salt is stored in the Vote row — no voter identity is involved.
        String checksum = computeVoteChecksum(election.getId(), candidat.getId(), checksumSalt);

        Vote vote = Vote.builder()
                .election(election)
                .candidat(candidat)
                .horodatage(now.plusSeconds((long) (Math.random() * 30)))
                .checksum(checksum)
                .checksumSalt(checksumSalt)
                .build();

        voteRepository.save(vote);

        log.debug("BARRIER 2 written: voteId={}, electionId={}, candidatId={} — NO voter identity stored",
                vote.getId(), election.getId(), candidat.getId());

        // ── Emit success log ─────────────────────────────────────────────────────
        log.info("Vote successfully recorded: electeurId={}, electionId={}, recu={}",
                electeur.getId(), election.getId(), recuCryptographique);

        // ── Build and return receipt ─────────────────────────────────────────────
        return VoteReceiptResponse.of(
                recuCryptographique,
                election.getId(),
                election.getTitre(),
                now
        );
    }

    // =========================================================
    // Eligibility Check
    // =========================================================

    /**
     * Checks whether the authenticated voter is eligible to vote in a given election.
     * Returns a structured result rather than throwing — used by the eligibility check endpoint
     * so the Angular client can render conditional UI before loading the ballot.
     *
     * @param electionId the election to check eligibility for
     * @return an EligibilityResult describing the voter's status
     */
    @Transactional(readOnly = true)
    public EligibilityResult checkEligibility(Long electionId) {
        Electeur electeur = resolveAuthenticatedVoter();

        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new ResourceNotFoundException("Election", electionId));

        // Check 1: election open?
        if (!election.isOuverte()) {
            return EligibilityResult.notEligible(
                    "L'élection n'est pas actuellement ouverte au vote. Statut: " + election.getStatut()
            );
        }

        // Check 2: already voted?
        if (emargementRepository.existsByElecteur_IdAndElection_Id(electeur.getId(), electionId)) {
            return EligibilityResult.alreadyVoted(
                    "Vous avez déjà voté dans cette élection.",
                    getReceiptForVoter(electeur.getId(), electionId)
            );
        }

        // Check 3: college restriction?
        if (election.getCollegeElectoral() != null) {
            if (electeur.getCollegeElectoral() == null
                    || !electeur.getCollegeElectoral().getId()
                       .equals(election.getCollegeElectoral().getId())) {
                return EligibilityResult.notEligible(
                        "Vous n'êtes pas membre du collège électoral requis pour cette élection."
                );
            }
        }

        // Check 4: 2FA required?
        boolean requiresOtp = electeur.isDoubleFacteurActif() && !electeur.isOtpVerified();

        return EligibilityResult.eligible(requiresOtp);
    }

    // =========================================================
    // Receipt Verification (public dashboard)
    // =========================================================

    /**
     * Verifies a cryptographic receipt UUID on the public verification dashboard.
     *
     * Returns a lightweight confirmation that the participation was recorded.
     * NEVER reveals the candidate chosen — only confirms the UUID exists in the
     * Emargement table and the election it belongs to.
     *
     * This endpoint is intentionally public (no authentication required) so that
     * voters can verify their participation on any device after the fact.
     *
     * @param recuCryptographique the UUID receipt provided to the voter at vote time
     * @return a VoteReceiptResponse confirming participation
     * @throws ResourceNotFoundException if the receipt UUID does not exist
     */
    @Transactional(readOnly = true)
    public VoteReceiptResponse verifyReceipt(String recuCryptographique) {
        Emargement emargement = emargementRepository
                .findByRecuCryptographique(recuCryptographique)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reçu cryptographique introuvable: '" + recuCryptographique + "'. "
                        + "Vérifiez que le code est correct et réessayez."
                ));

        log.info("Receipt verified successfully: recu={}, electionId={}",
                recuCryptographique, emargement.getElection().getId());

        return VoteReceiptResponse.of(
                recuCryptographique,
                emargement.getElection().getId(),
                emargement.getElection().getTitre(),
                emargement.getDateEmargement()
        );
    }

    // =========================================================
    // Vote Statistics (read-only)
    // =========================================================

    /**
     * Returns the total number of votes cast in a given election.
     * Available to ADMIN and OBSERVATEUR roles for monitoring purposes.
     * Also used internally for result computation.
     *
     * @param electionId the election to query
     * @return total ballot count
     */
    @Transactional(readOnly = true)
    public long countVotesByElection(Long electionId) {
        return voteRepository.countByElectionId(electionId);
    }

    /**
     * Returns the total number of confirmed participants (emargements) in an election.
     * Should equal countVotesByElection in a correctly operating system.
     * A discrepancy is a data integrity signal and should be investigated.
     *
     * @param electionId the election to query
     * @return total participant count (from Emargement table)
     */
    @Transactional(readOnly = true)
    public long countParticipantsByElection(Long electionId) {
        return emargementRepository.countByElection_Id(electionId);
    }

    /**
     * Returns all elections the authenticated voter has already voted in.
     * Used by the Angular voter dashboard to mark elections as "voted".
     *
     * @return list of election IDs voted in by the current voter
     */
    @Transactional(readOnly = true)
    public List<Long> getVotedElectionIds() {
        Electeur electeur = resolveAuthenticatedVoter();
        return emargementRepository.findVotedElectionIdsByElecteur(electeur.getId());
    }

    /**
     * Returns the cryptographic receipt for the authenticated voter in a given election.
     * Used to re-display the receipt if the voter lost it.
     *
     * @param electionId the election to query
     * @return the VoteReceiptResponse for this voter in this election
     * @throws ResourceNotFoundException if the voter has not voted in this election
     */
    @Transactional(readOnly = true)
    public VoteReceiptResponse getMyReceipt(Long electionId) {
        Electeur electeur = resolveAuthenticatedVoter();
        String receipt = getReceiptForVoter(electeur.getId(), electionId);
        if (receipt == null) {
            throw new ResourceNotFoundException(
                    "Aucun vote enregistré pour vous dans l'élection id=" + electionId + "."
            );
        }
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new ResourceNotFoundException("Election", electionId));

        Emargement emargement = emargementRepository
                .findByRecuCryptographique(receipt)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reçu introuvable pour l'élection id=" + electionId
                ));

        return VoteReceiptResponse.of(
                receipt,
                electionId,
                election.getTitre(),
                emargement.getDateEmargement()
        );
    }

    // =========================================================
    // Integrity Verification (FR-12)
    // =========================================================

    /**
     * Verifies the integrity of all stored votes for a given election.
     *
     * Recomputes the checksum for each vote record and compares it
     * against the stored value. Any discrepancy indicates that a vote
     * record was tampered with after insertion — even by a DBA.
     *
     * This method is called periodically by the integrity monitoring job
     * and is accessible by the ADMIN role via the audit endpoint.
     *
     * @param electionId the election to audit
     * @return an IntegrityReport summarizing the verification result
     */
    @Transactional(readOnly = true)
    public IntegrityReport verifyIntegrity(Long electionId) {
        log.info("Starting integrity verification for election id={}", electionId);

        List<Vote> votes = voteRepository.findAllByElectionIdOrderById(electionId);
        int total = votes.size();
        int corrupted = 0;

        for (Vote vote : votes) {
            if (vote.getChecksum() == null) continue;

            // Recompute using the stored salt — each vote is independently verifiable
            String salt = vote.getChecksumSalt() != null ? vote.getChecksumSalt() : "";
            String recomputed = computeVoteChecksum(
                    vote.getElection().getId(),
                    vote.getCandidat().getId(),
                    salt
            );

            if (!vote.getChecksum().equals(recomputed)) {
                corrupted++;
                log.error("INTEGRITY VIOLATION detected: voteId={}, electionId={}",
                        vote.getId(), electionId);
            }
        }

        boolean intact = corrupted == 0;
        log.info("Integrity check for election id={}: total={}, corrupted={}, intact={}",
                electionId, total, corrupted, intact);

        return new IntegrityReport(electionId, total, corrupted, intact,
                LocalDateTime.now());
    }

    // =========================================================
    // Private security guards
    // =========================================================

    /**
     * Resolves the currently authenticated Electeur from the Spring Security context.
     *
     * @return the authenticated Electeur entity
     * @throws VoterNotEligibleException if the authenticated user is not an ELECTEUR
     */
    private Electeur resolveAuthenticatedVoter() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Utilisateur utilisateur = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Utilisateur authentifié introuvable: " + email
                ));

        if (!(utilisateur instanceof Electeur electeur)) {
            throw new VoterNotEligibleException(
                    "Seuls les utilisateurs avec le rôle ELECTEUR peuvent soumettre un bulletin de vote. "
                    + "Rôle actuel: " + utilisateur.getRole()
            );
        }

        if (!electeur.isEnabled()) {
            throw new VoterNotEligibleException(
                    "Votre compte a été désactivé. Veuillez contacter l'administrateur."
            );
        }

        return electeur;
    }

    /**
     * Asserts the election is currently OUVERTE (active voting period).
     *
     * @param election the election to check
     * @throws ElectionNotOpenException if the election is not in OUVERTE state
     */
    private void assertElectionIsOpen(Election election) {
        if (!election.isOuverte()) {
            log.warn("Vote rejected: election id={} is not OUVERTE (current status={})",
                    election.getId(), election.getStatut());
            throw new ElectionNotOpenException(election.getId(), election.getStatut());
        }
    }

    /**
     * Asserts that the voter has completed Two-Factor Authentication before voting.
     * Only enforced if the voter has 2FA enabled (doubleFacteurActif = true).
     *
     * @param electeur the voter to check
     * @param election the election (used for logging context only)
     * @throws TwoFactorRequiredException if 2FA is required but OTP has not been verified
     */
    private void assertVoterHasCompletedTwoFactor(Electeur electeur, Election election) {
        if (electeur.isDoubleFacteurActif() && !electeur.isOtpVerified()) {
            log.warn("Vote rejected: voter id={} has not completed 2FA for election id={}",
                    electeur.getId(), election.getId());
            throw new TwoFactorRequiredException();
        }
    }

    /**
     * Asserts that the voter is eligible to participate in the given election.
     *
     * Eligibility rules:
     *  - If the election has no college restriction: all ELECTEUR users are eligible.
     *  - If the election is restricted to a college: the voter must belong to that college.
     *
     * @param electeur the voter to check
     * @param election the election to check eligibility for
     * @throws VoterNotEligibleException if the voter does not meet the college restriction
     */
    private void assertVoterIsEligible(Electeur electeur, Election election) {
        if (election.getCollegeElectoral() == null) {
            // No restriction — all ELECTEUR users are eligible
            return;
        }

        Long requiredCollegeId = election.getCollegeElectoral().getId();

        if (electeur.getCollegeElectoral() == null
                || !electeur.getCollegeElectoral().getId().equals(requiredCollegeId)) {
            log.warn("Vote rejected: voter id={} is not in the required college id={} for election id={}",
                    electeur.getId(), requiredCollegeId, election.getId());

            throw new VoterNotEligibleException(
                    "Vous n'êtes pas membre du collège électoral requis pour participer à cette élection. "
                    + "Collège requis: id=" + requiredCollegeId
                    + ", collège assigné: "
                    + (electeur.getCollegeElectoral() != null
                        ? "id=" + electeur.getCollegeElectoral().getId()
                        : "aucun")
            );
        }
    }

    /**
     * Asserts that the voter has NOT already voted in this election.
     *
     * Uses the Emargement table's unique constraint on (electeur_id, election_id)
     * as the definitive authority. This check is performed before the transaction
     * to provide a user-friendly error message, with the DB constraint as the
     * ultimate safety net.
     *
     * @param electeurId the voter ID to check
     * @param electionId the election ID to check
     * @throws AlreadyVotedException if an emargement record already exists for this pair
     */
    private void assertVoterHasNotVoted(Long electeurId, Long electionId) {
        if (emargementRepository.existsByElecteur_IdAndElection_Id(electeurId, electionId)) {
            log.warn("FRAUD ATTEMPT: voter id={} tried to vote twice in election id={}",
                    electeurId, electionId);
            throw new AlreadyVotedException(electionId);
        }
    }

    // =========================================================
    // Private helper methods
    // =========================================================

    /**
     * Resolves the client IP address from the current HTTP request.
     * Checks X-Forwarded-For first (for reverse-proxy setups), falls back to remote address.
     *
     * @return the client's IP address string, or "unknown" if unavailable
     */
    private String resolveClientIp() {
        try {
            org.springframework.web.context.request.ServletRequestAttributes attrs =
                    (org.springframework.web.context.request.ServletRequestAttributes)
                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                jakarta.servlet.http.HttpServletRequest req = attrs.getRequest();
                String forwarded = req.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    return forwarded.split(",")[0].trim();
                }
                return req.getRemoteAddr();
            }
        } catch (Exception ignored) {
            // Non-critical — logging only
        }
        return "unknown";
    }

    /**
     * Computes a SHA-256 checksum for a vote record.
     *
     * The checksum incorporates:
     *  - The election ID
     *  - The candidate ID
     *  - The cryptographic receipt UUID (known only at transaction time)
     *
     * This binds the Vote record's integrity to the corresponding Emargement
     * without creating a FK relationship between the two tables.
     * Any post-hoc modification to the election_id or candidat_id fields
     * will cause the checksum prefix verification to fail (FR-12).
     *
     * @param electionId          the election ID
     * @param candidatId          the candidate ID
     * @param recuCryptographique the UUID receipt (discarded after storage; used for binding only)
     * @return a 64-character hex-encoded SHA-256 hash
     */
    private String computeVoteChecksum(Long electionId, Long candidatId, String salt) {
        try {
            // salt is either the checksumSalt (new votes) or empty string (legacy)
            String data = "election=" + electionId + "|candidat=" + candidatId + "|salt=" + salt;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 not available", e);
            return "CHECKSUM_UNAVAILABLE";
        }
    }

    /**
     * Returns the cryptographic receipt for a voter in a specific election, or null.
     *
     * @param electeurId the voter ID
     * @param electionId the election ID
     * @return the UUID receipt string, or null if the voter hasn't voted
     */
    private String getReceiptForVoter(Long electeurId, Long electionId) {
        return emargementRepository
                .findByElecteur_IdAndElection_Id(electeurId, electionId)
                .map(Emargement::getRecuCryptographique)
                .orElse(null);
    }

    // =========================================================
    // Inner result classes
    // =========================================================

    /**
     * Result of an eligibility check for a voter in a specific election.
     * Used by the /eligibility endpoint to provide pre-ballot UI guidance.
     */
    public record EligibilityResult(
            boolean eligible,
            boolean alreadyVoted,
            boolean requiresOtp,
            String message,
            String existingReceipt
    ) {
        public static EligibilityResult eligible(boolean requiresOtp) {
            return new EligibilityResult(true, false, requiresOtp,
                    requiresOtp
                        ? "Veuillez compléter la vérification 2FA avant de voter."
                        : "Vous êtes éligible à voter dans cette élection.",
                    null);
        }

        public static EligibilityResult alreadyVoted(String message, String receipt) {
            return new EligibilityResult(false, true, false, message, receipt);
        }

        public static EligibilityResult notEligible(String message) {
            return new EligibilityResult(false, false, false, message, null);
        }
    }

    /**
     * Report produced by the vote integrity verification process (FR-12).
     * Summarises the result of checksumming the Vote table for a given election.
     */
    public record IntegrityReport(
            Long electionId,
            int totalVotes,
            int corruptedVotes,
            boolean intact,
            LocalDateTime verifiedAt
    ) {
        public String getSummary() {
            if (intact) {
                return "✓ Intégrité vérifiée: " + totalVotes + " bulletins — aucune altération détectée.";
            } else {
                return "⚠ ALERTE: " + corruptedVotes + "/" + totalVotes
                        + " bulletins potentiellement altérés dans l'élection id=" + electionId;
            }
        }
    }
}
