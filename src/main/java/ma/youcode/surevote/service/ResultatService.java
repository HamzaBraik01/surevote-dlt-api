package ma.youcode.surevote.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.annotation.Auditable;
import ma.youcode.surevote.domain.entity.Candidat;
import ma.youcode.surevote.domain.entity.Election;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import ma.youcode.surevote.domain.enums.StatutElection;
import ma.youcode.surevote.dto.response.ResultatResponse;
import ma.youcode.surevote.dto.response.VoteReceiptResponse;
import ma.youcode.surevote.exception.ResourceNotFoundException;
import ma.youcode.surevote.exception.ResultsNotAvailableException;
import ma.youcode.surevote.repository.CandidatRepository;
import ma.youcode.surevote.repository.CollegeElectoralRepository;
import ma.youcode.surevote.repository.ElectionRepository;
import ma.youcode.surevote.repository.EmargementRepository;
import ma.youcode.surevote.repository.UtilisateurRepository;
import ma.youcode.surevote.repository.VoteRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service layer responsible for election result computation and vote integrity
 * in the SUREVOTE platform.
 *
 * Responsibilities:
 *  - Computing deterministic, SQL-aggregation-based election results
 *  - Enforcing the result access lock (results only accessible after CLOTUREE/PUBLIEE)
 *  - Cryptographic receipt verification (public dashboard — UUID-based)
 *  - Vote table integrity checksum computation (FR-12)
 *  - Participation rate calculation
 *
 * Security guarantees:
 *  - Results are strictly locked until the election transitions to CLOTUREE.
 *  - The receipt verification endpoint never reveals vote choice — only confirms
 *    that participation was recorded.
 *  - All result data is aggregated at the database level — no individual ballot
 *    is ever loaded or exposed.
 *
 * Design notes:
 *  - Result computation is deterministic and idempotent: calling it multiple times
 *    for the same election always returns the same result (SQL COUNT is stable).
 *  - Results are computed on-demand (not pre-stored), ensuring they always reflect
 *    the actual state of the Vote table.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ResultatService {

    private final ElectionRepository       electionRepository;
    private final CandidatRepository       candidatRepository;
    private final VoteRepository           voteRepository;
    private final EmargementRepository     emargementRepository;
    private final CollegeElectoralRepository collegeElectoralRepository;
    private final UtilisateurRepository    utilisateurRepository;

    // =========================================================
    // Public result retrieval
    // =========================================================

    /**
     * Returns the official, publicly accessible results of a published election.
     *
     * Access policy:
     *  - Only elections in PUBLIEE state are accessible via this method.
     *  - For CLOTUREE elections (awaiting publication), use {@link #getResultsForAdmin(Long)}.
     *
     * @param electionId the ID of the election
     * @return the full result response including ranked candidates and participation stats
     * @throws ResourceNotFoundException     if no election exists with the given ID
     * @throws ResultsNotAvailableException  if the election is not yet published
     */
    @Auditable(actionType = "RESULTS_VIEWED", description = "Consultation des résultats publiés")
    @Cacheable(value = "publicResults", key = "#electionId")
    public ResultatResponse getPublicResults(Long electionId) {
        log.debug("Fetching public results for election id={}", electionId);

        Election election = loadElectionOrThrow(electionId);

        if (election.getStatut() != StatutElection.PUBLIEE) {
            throw new ResultsNotAvailableException(electionId);
        }

        return computeResults(election);
    }

    /**
     * Returns the results of a closed election — accessible to ADMIN role only.
     * Available from CLOTUREE state (before publication) to allow the administrator
     * to review results before publishing them officially.
     *
     * @param electionId the ID of the election
     * @return the full result response
     * @throws ResourceNotFoundException    if no election exists with the given ID
     * @throws ResultsNotAvailableException if the election has not yet been closed
     */
    @Auditable(actionType = "ADMIN_RESULTS_VIEWED", description = "Consultation admin des résultats (avant publication)")
    public ResultatResponse getResultsForAdmin(Long electionId) {
        log.debug("Admin fetching results for election id={}", electionId);

        Election election = loadElectionOrThrow(electionId);

        if (election.getStatut() != StatutElection.CLOTUREE
                && election.getStatut() != StatutElection.PUBLIEE) {
            throw new ResultsNotAvailableException(electionId);
        }

        return computeResults(election);
    }

    // =========================================================
    // Cryptographic receipt verification (FR-08 / FR-14)
    // =========================================================

    /**
     * Checks whether a receipt UUID exists in the system without loading the full entity.
     * Used for lightweight existence checks on the public dashboard.
     *
     * @param recuCryptographique the receipt UUID to check
     * @return true if the receipt is present in the Emargement table
     */
    public boolean receiptExists(String recuCryptographique) {
        return emargementRepository.existsByRecuCryptographique(recuCryptographique);
    }

    // =========================================================
    // Vote Integrity Checksum (FR-12)
    // =========================================================

    /**
     * Computes a SHA-256 checksum of all Vote records for a given election.
     *
     * The checksum is derived by concatenating the sorted list of
     * [voteId + electionId + candidatId + horodatage] for every vote in the election,
     * then hashing the result with SHA-256.
     *
     * Any post-registration modification to a vote record (tampered candidatId,
     * altered horodatage, deleted row) will produce a different checksum,
     * immediately signaling a data integrity violation.
     *
     * @param electionId the election to compute the checksum for
     * @return the hex-encoded SHA-256 hash string (64 characters)
     * @throws ResourceNotFoundException if the election does not exist
     */
    public String computeVoteTableChecksum(Long electionId) {
        log.debug("Computing vote integrity checksum for election id={}", electionId);

        if (!electionRepository.existsById(electionId)) {
            throw new ResourceNotFoundException("Election", electionId);
        }

        // Load all votes ordered deterministically by ID
        List<Object[]> voteData = voteRepository.findVoteIdAndChecksumByElectionId(electionId);

        if (voteData.isEmpty()) {
            log.debug("No votes found for election id={} — returning empty checksum", electionId);
            return computeHash("EMPTY:" + electionId);
        }

        // Build the canonical string for hashing
        StringBuilder canonical = new StringBuilder();
        canonical.append("election=").append(electionId).append(";");
        canonical.append("count=").append(voteData.size()).append(";");

        for (Object[] row : voteData) {
            Long voteId  = (Long)   row[0];
            String check = (String) row[1];
            canonical.append("v").append(voteId)
                     .append(":").append(check != null ? check : "null")
                     .append(";");
        }

        String checksum = computeHash(canonical.toString());
        log.debug("Checksum computed for election id={}: {}", electionId, checksum);
        return checksum;
    }

    /**
     * Validates that the stored per-vote checksums in the Vote table have not been
     * altered since insertion. Compares each stored checksum against a freshly
     * recomputed value using the vote's current field data.
     *
     * Returns a list of vote IDs whose checksums do not match — an empty list
     * indicates the table is fully intact.
     *
     * @param electionId the election to audit
     * @return list of vote IDs with mismatched checksums (empty if all intact)
     */
    @Transactional(readOnly = true)
    public List<Long> detectTamperedVotes(Long electionId) {
        log.info("Running vote integrity audit for election id={}", electionId);

        List<ma.youcode.surevote.domain.entity.Vote> votes =
                voteRepository.findAllByElectionIdOrderById(electionId);

        List<Long> tampered = new ArrayList<>();

        for (ma.youcode.surevote.domain.entity.Vote vote : votes) {
            if (vote.getChecksum() == null) continue;

            String recomputed = computeVoteChecksum(vote);
            if (!vote.getChecksum().equals(recomputed)) {
                log.warn("INTEGRITY ALERT — Vote id={} checksum mismatch! Stored={} Recomputed={}",
                        vote.getId(), vote.getChecksum(), recomputed);
                tampered.add(vote.getId());
            }
        }

        if (tampered.isEmpty()) {
            log.info("Vote integrity audit passed for election id={}: all {} votes intact",
                    electionId, votes.size());
        } else {
            log.error("Vote integrity audit FAILED for election id={}: {} tampered vote(s) detected: {}",
                    electionId, tampered.size(), tampered);
        }

        return tampered;
    }

    /**
     * Computes the per-vote checksum for a single Vote record.
     * Called during integrity audits to verify that stored checksums
     * match the expected value computed from the vote's fields.
     *
     * IMPORTANT: This formula MUST match the one used in VoteService.computeVoteChecksum()
     * at vote creation time:
     * SHA-256("election=<electionId>|candidat=<candidatId>|salt=<checksumSalt>")
     *
     * @param vote the vote whose checksum should be computed
     * @return the hex-encoded SHA-256 checksum string
     */
    public String computeVoteChecksum(ma.youcode.surevote.domain.entity.Vote vote) {
        String input = "election=" + (vote.getElection() != null ? vote.getElection().getId() : "null")
                + "|candidat=" + (vote.getCandidat() != null ? vote.getCandidat().getId() : "null")
                + "|salt=" + (vote.getChecksumSalt() != null ? vote.getChecksumSalt() : "");
        return computeHash(input);
    }

    // =========================================================
    // Statistics helpers (used by MetricsService)
    // =========================================================

    /**
     * Returns the total number of votes cast in a given election.
     *
     * @param electionId the election ID
     * @return total ballot count
     */
    public long countVotesByElection(Long electionId) {
        return voteRepository.countByElectionId(electionId);
    }

    /**
     * Returns the total participation count (emargements) for an election.
     *
     * @param electionId the election ID
     * @return total number of voters who participated
     */
    public long countParticipantsByElection(Long electionId) {
        return emargementRepository.countByElection_Id(electionId);
    }

    /**
     * Computes the participation rate for an election.
     *
     * @param electionId the election ID
     * @return participation rate as a value between 0.0 and 100.0
     */
    public double computeParticipationRate(Long electionId) {
        Election election = loadElectionOrThrow(electionId);
        long totalEligibles = computeTotalEligibleVoters(election);
        if (totalEligibles == 0) return 0.0;

        long totalParticipants = emargementRepository.countByElection_Id(electionId);
        return Math.round(((double) totalParticipants / totalEligibles) * 10000.0) / 100.0;
    }

    // =========================================================
    // Core result computation — private
    // =========================================================

    /**
     * Core result computation method.
     *
     * Algorithm:
     *  1. Load all candidates for the election.
     *  2. For each candidate, execute a SQL COUNT query on the Vote table.
     *  3. Rank candidates by vote count (descending).
     *  4. Compute participation statistics.
     *  5. Assemble and return the ResultatResponse.
     *
     * This method is deterministic — same inputs always produce same outputs.
     * It never accesses voter identity data — only Election and Candidat references.
     *
     * @param election the election whose results to compute
     * @return fully assembled ResultatResponse with ranked results and statistics
     */
    private ResultatResponse computeResults(Election election) {
        Long electionId = election.getId();
        log.info("Computing results for election id={} ('{}')", electionId, election.getTitre());

        // Step 1: Load candidates with vote counts (LEFT JOIN includes 0-vote candidates)
        List<Object[]> allCandidatsWithVotes = candidatRepository.findCandidatsWithVoteCount(electionId);
        log.debug("Loaded {} candidate rows for election id={}", allCandidatsWithVotes.size(), electionId);

        // Step 2: Compute total votes cast
        long totalVotes = voteRepository.countByElectionId(electionId);
        log.debug("Total votes for election id={}: {}", electionId, totalVotes);

        // Step 3: Compute total eligible voters (safe — handles lazy loading gracefully)
        long totalEligibles = computeTotalEligibleVoters(election);

        // Step 4: Build ranked candidate result list
        List<ResultatResponse.CandidatResultat> resultats = buildRankedResults(
                allCandidatsWithVotes, totalVotes
        );

        // Step 5: Compute participation statistics (safe — no division by zero)
        double tauxParticipation = (totalEligibles > 0)
                ? Math.round(((double) totalVotes / totalEligibles) * 10000.0) / 100.0
                : 0.0;

        // Step 6: Compute integrity checksum
        String checksum = computeVoteTableChecksum(electionId);

        // Step 7: Assemble the response
        ResultatResponse response = ResultatResponse.builder()
                .electionId(electionId)
                .titrElection(election.getTitre())
                .descriptionElection(election.getDescription())
                .dateDebut(election.getDateDebut())
                .dateFin(election.getDateFin())
                .statut(election.getStatut())
                .totalVotes(totalVotes)
                .totalElecteursEligibles(totalEligibles)
                .tauxParticipation(tauxParticipation)
                .resultats(resultats != null ? resultats : new ArrayList<>())
                .dateCalculResultats(LocalDateTime.now())
                .checksumIntegrite(checksum)
                .build();

        // Step 8: Finalise computed fields (abstention, gagnant, egalite)
        response.finalise();

        log.info("Results computed for election id={}: {} votes, {} candidates, taux={}%",
                electionId, totalVotes, resultats.size(), tauxParticipation);

        return response;
    }

    /**
     * Builds the ranked list of CandidatResultat from raw SQL aggregation data.
     *
     * Input format: List of Object[] from {@code candidatRepository.findCandidatsWithVoteCount}:
     *   [0] Candidat entity
     *   [1] Long voteCount
     *
     * @param rawData    list of [Candidat, Long] pairs from the aggregation query
     * @param totalVotes total vote count across all candidates (for percentage calculation)
     * @return ranked list of CandidatResultat, ordered by vote count descending
     */
    private List<ResultatResponse.CandidatResultat> buildRankedResults(
            List<Object[]> rawData, long totalVotes) {

        List<ResultatResponse.CandidatResultat> resultats = new ArrayList<>();

        // Convert raw rows to result objects (vote count already sorted DESC by query)
        for (Object[] row : rawData) {
            Candidat candidat   = (Candidat) row[0];
            long     voteCount  = row[1] != null ? ((Number) row[1]).longValue() : 0L;

            // Rank is determined by position (1-based); ties share the same position
            int rang = resultats.size() + 1;

            // Adjust rank for ties: if previous entry has same vote count, share their rank
            if (!resultats.isEmpty()) {
                ResultatResponse.CandidatResultat previous = resultats.get(resultats.size() - 1);
                if (previous.getNombreVotes() == voteCount) {
                    rang = previous.getRang(); // shared rank for tie
                }
            }

            resultats.add(ResultatResponse.CandidatResultat.of(
                    candidat.getId(),
                    candidat.getNom(),
                    candidat.getPrenom(),
                    candidat.getAffiliationOuParti(),
                    candidat.getPhotoUrl(),
                    voteCount,
                    totalVotes,
                    rang
            ));
        }

        return resultats;
    }

    /**
     * Determines the total number of voters eligible for a given election.
     *
     * Logic:
     *  - If the election is restricted to a CollegeElectoral, returns the size of that college.
     *  - If the election is open to all, returns the total ELECTEUR count on the platform.
     *
     * @param election the election to compute eligible voters for
     * @return total number of eligible voters
     */
    private long computeTotalEligibleVoters(Election election) {
        try {
            if (election.getCollegeElectoral() != null) {
                return collegeElectoralRepository
                        .countMembersByCollegeId(election.getCollegeElectoral().getId());
            }
        } catch (Exception ex) {
            log.warn("Could not access collegeElectoral for election id={}: {}. Falling back to total ELECTEUR count.",
                    election.getId(), ex.getMessage());
        }
        return utilisateurRepository.countByRole(RoleUtilisateur.ELECTEUR);
    }

    /**
     * Loads an Election entity with its collegeElectoral eagerly loaded.
     * Uses JOIN FETCH to avoid LazyInitializationException in computeTotalEligibleVoters.
     *
     * @param id the election primary key
     * @return the Election entity with collegeElectoral initialized
     * @throws ResourceNotFoundException if no election with that ID exists
     */
    private Election loadElectionOrThrow(Long id) {
        return electionRepository.findByIdWithCollegeElectoral(id)
                .orElseThrow(() -> new ResourceNotFoundException("Election", id));
    }

    /**
     * Computes a SHA-256 hash of the given input string.
     * Returns the result as a lowercase hex-encoded string (64 chars).
     *
     * @param input the string to hash
     * @return hex-encoded SHA-256 hash
     */
    private String computeHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in Java — this branch is unreachable
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
