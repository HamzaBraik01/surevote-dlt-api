package ma.youcode.surevote.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.annotation.Auditable;
import ma.youcode.surevote.domain.entity.Candidat;
import ma.youcode.surevote.domain.entity.Election;
import ma.youcode.surevote.domain.enums.StatutElection;
import ma.youcode.surevote.dto.request.CandidatRequest;
import ma.youcode.surevote.dto.response.CandidatResponse;
import ma.youcode.surevote.exception.DuplicateResourceException;
import ma.youcode.surevote.exception.InvalidElectionStateException;
import ma.youcode.surevote.exception.ResourceNotFoundException;
import ma.youcode.surevote.mapper.CandidatMapper;
import ma.youcode.surevote.repository.CandidatRepository;
import ma.youcode.surevote.repository.ElectionRepository;
import ma.youcode.surevote.repository.VoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service layer for Candidat (candidate) management.
 *
 * Responsibilities:
 *  - Adding candidates to an election (BROUILLON or PLANIFIEE state only)
 *  - Updating candidate information
 *  - Removing candidates (only when no votes have been cast)
 *  - Listing candidates per election
 *  - Mapping entities to response DTOs
 *
 * Business rules:
 *  - Candidates can only be added/modified when the election is in BROUILLON or PLANIFIEE state.
 *  - Candidates cannot be removed once votes have been cast for them.
 *  - Duplicate candidates (same nom + prenom in the same election) are rejected.
 *  - At least 2 candidates are required before an election can be opened.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CandidatService {

    private final CandidatRepository candidatRepository;
    private final ElectionRepository electionRepository;
    private final VoteRepository voteRepository;
    private final CandidatMapper candidatMapper;

    // =========================================================
    // Read operations
    // =========================================================

    /**
     * Returns all candidates registered for a specific election.
     * Available to all authenticated users when the election is OUVERTE, CLOTUREE, or PUBLIEE.
     *
     * @param electionId the ID of the election
     * @return list of candidates, ordered by last name ascending
     * @throws ResourceNotFoundException if the election does not exist
     */
    public List<CandidatResponse> findAllByElection(Long electionId) {
        log.debug("Fetching candidates for election id={}", electionId);

        // Verify election exists
        if (!electionRepository.existsById(electionId)) {
            throw new ResourceNotFoundException("Election", electionId);
        }

        return candidatRepository.findByElectionId(electionId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Returns a single candidate by their ID.
     *
     * @param candidatId the candidate's primary key
     * @return the candidate's response DTO
     * @throws ResourceNotFoundException if no candidate exists with the given ID
     */
    public CandidatResponse findById(Long candidatId) {
        log.debug("Fetching candidat id={}", candidatId);
        Candidat candidat = loadCandidatById(candidatId);
        return toResponse(candidat);
    }

    /**
     * Returns the total number of candidates registered for an election.
     * Used for validation before opening an election.
     *
     * @param electionId the election ID
     * @return count of candidates
     */
    public long countByElection(Long electionId) {
        return candidatRepository.countByElectionId(electionId);
    }

    // =========================================================
    // Write operations (ADMIN only)
    // =========================================================

    /**
     * Adds a new candidate to an election.
     *
     * Pre-conditions:
     *  - The election must exist.
     *  - The election must be in BROUILLON or PLANIFIEE state (not yet open).
     *  - No candidate with the same nom + prenom already exists in the election.
     *
     * @param electionId the ID of the election to add the candidate to
     * @param request    the candidate's profile data
     * @return the created candidate's response DTO
     * @throws ResourceNotFoundException      if the election does not exist
     * @throws InvalidElectionStateException  if the election is not in a modifiable state
     * @throws DuplicateResourceException     if a candidate with the same name already exists
     */
    @Transactional
    @Auditable(actionType = "CANDIDAT_ADDED", description = "Nouveau candidat ajouté à l'élection")
    public CandidatResponse addCandidat(Long electionId, CandidatRequest request) {
        log.info("Adding candidate '{}  {}' to election id={}", request.getPrenom(), request.getNom(), electionId);

        Election election = loadElectionById(electionId);
        assertElectionIsModifiable(election);
        assertNoDuplicateCandidat(request.getNom(), request.getPrenom(), electionId);

        Candidat candidat = candidatMapper.toEntity(request);
        candidat.setNom(candidat.getNom().trim());
        candidat.setPrenom(candidat.getPrenom().trim());
        candidat.setElection(election);

        Candidat saved = candidatRepository.save(candidat);
        log.info("Candidate id={} created: '{}' for election id={}", saved.getId(), saved.getNomComplet(), electionId);

        return toResponse(saved);
    }

    /**
     * Updates an existing candidate's profile information.
     *
     * Pre-conditions:
     *  - The candidate must exist.
     *  - The linked election must still be in BROUILLON or PLANIFIEE state.
     *
     * @param candidatId the ID of the candidate to update
     * @param request    the updated candidate data
     * @return the updated candidate's response DTO
     * @throws ResourceNotFoundException      if the candidate does not exist
     * @throws InvalidElectionStateException  if the linked election is not modifiable
     */
    @Transactional
    @Auditable(actionType = "CANDIDAT_UPDATED", description = "Profil candidat mis à jour")
    public CandidatResponse updateCandidat(Long candidatId, CandidatRequest request) {
        log.info("Updating candidate id={}", candidatId);

        Candidat candidat = loadCandidatById(candidatId);
        assertElectionIsModifiable(candidat.getElection());

        // Check for duplicate name only if name has changed
        boolean nameChanged = !candidat.getNom().equalsIgnoreCase(request.getNom())
                || !candidat.getPrenom().equalsIgnoreCase(request.getPrenom());

        if (nameChanged) {
            assertNoDuplicateCandidat(request.getNom(), request.getPrenom(),
                    candidat.getElection().getId());
        }

        candidatMapper.updateEntity(request, candidat);
        candidat.setNom(candidat.getNom().trim());
        candidat.setPrenom(candidat.getPrenom().trim());
        if (candidat.getPhotoUrl() != null) {
            candidat.setPhotoUrl(candidat.getPhotoUrl().trim());
        }
        if (candidat.getProgrammePdfUrl() != null) {
            candidat.setProgrammePdfUrl(candidat.getProgrammePdfUrl().trim());
        }

        Candidat updated = candidatRepository.save(candidat);
        log.info("Candidate id={} updated successfully", candidatId);

        return toResponse(updated);
    }

    /**
     * Removes a candidate from an election.
     *
     * Pre-conditions:
     *  - The candidate must exist.
     *  - The linked election must be in BROUILLON or PLANIFIEE state.
     *  - No votes must have been cast for this candidate.
     *
     * @param candidatId the ID of the candidate to remove
     * @throws ResourceNotFoundException     if the candidate does not exist
     * @throws InvalidElectionStateException if the election is not in a modifiable state
     * @throws IllegalStateException         if votes have already been cast for this candidate
     */
    @Transactional
    @Auditable(actionType = "CANDIDAT_REMOVED", description = "Candidat retiré de l'élection")
    public void deleteCandidat(Long candidatId) {
        log.info("Removing candidate id={}", candidatId);

        Candidat candidat = loadCandidatById(candidatId);
        assertElectionIsModifiable(candidat.getElection());

        // Safety check: refuse deletion if votes exist for this candidate
        if (voteRepository.existsByCandidatId(candidatId)) {
            throw new IllegalStateException(
                    "Impossible de supprimer le candidat id=" + candidatId
                    + ": des votes ont déjà été enregistrés pour ce candidat."
            );
        }

        candidatRepository.deleteById(candidatId);
        log.info("Candidate id={} removed from election id={}", candidatId,
                candidat.getElection().getId());
    }

    /**
     * Updates only the photo URL of a candidate (used after a dedicated file upload).
     *
     * @param candidatId the candidate ID
     * @param photoUrl   the new photo URL
     * @return the updated response DTO
     */
    @Transactional
    @Auditable(actionType = "CANDIDAT_PHOTO_UPDATED", description = "Photo candidat mise à jour")
    public CandidatResponse updatePhotoUrl(Long candidatId, String photoUrl) {
        Candidat candidat = loadCandidatById(candidatId);
        assertElectionIsModifiable(candidat.getElection());
        candidat.setPhotoUrl(photoUrl);
        return toResponse(candidatRepository.save(candidat));
    }

    /**
     * Updates only the programme PDF URL of a candidate.
     *
     * @param candidatId      the candidate ID
     * @param programmePdfUrl the new PDF URL
     * @return the updated response DTO
     */
    @Transactional
    @Auditable(actionType = "CANDIDAT_PDF_UPDATED", description = "Programme PDF candidat mis à jour")
    public CandidatResponse updateProgrammePdfUrl(Long candidatId, String programmePdfUrl) {
        Candidat candidat = loadCandidatById(candidatId);
        assertElectionIsModifiable(candidat.getElection());
        candidat.setProgrammePdfUrl(programmePdfUrl);
        return toResponse(candidatRepository.save(candidat));
    }

    // =========================================================
    // Internal entity loader helpers
    // =========================================================

    /**
     * Loads a Candidat entity by ID or throws ResourceNotFoundException.
     *
     * @param id the candidate primary key
     * @return the loaded Candidat entity
     */
    public Candidat loadCandidatById(Long id) {
        return candidatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidat", id));
    }

    /**
     * Loads a Candidat that belongs to a specific election, or throws.
     * Used to prevent cross-election candidate injection in vote submission.
     *
     * @param candidatId the candidate primary key
     * @param electionId the expected election ID
     * @return the Candidat entity if it belongs to the given election
     * @throws ResourceNotFoundException if the candidate does not exist in that election
     */
    public Candidat loadCandidatForElection(Long candidatId, Long electionId) {
        return candidatRepository.findByIdAndElectionId(candidatId, electionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Candidat id=" + candidatId
                        + " introuvable dans l'élection id=" + electionId
                        + ". Vérifiez que le candidat appartient bien à cette élection."
                ));
    }

    // =========================================================
    // DTO Mapping
    // =========================================================

    /**
     * Maps a Candidat entity to a CandidatResponse DTO.
     * Does NOT populate vote count fields (only populated in result views).
     *
     * @param candidat the entity to map
     * @return the mapped DTO
     */
    public CandidatResponse toResponse(Candidat candidat) {
        return candidatMapper.toResponse(candidat);
    }

    /**
     * Maps a Candidat entity to a CandidatResponse DTO WITH result data.
     * Used exclusively by the result computation service (ResultatService).
     *
     * @param candidat       the entity to map
     * @param nombreVotes    vote count for this candidate
     * @param totalVotes     total votes in the election (for percentage calculation)
     * @param rang           ranking position (1 = winner)
     * @return the mapped DTO including result fields
     */
    public CandidatResponse toResponseWithResults(Candidat candidat, long nombreVotes,
                                                   long totalVotes, int rang) {
        double pourcentage = (totalVotes > 0)
                ? Math.round(((double) nombreVotes / totalVotes) * 10000.0) / 100.0
                : 0.00;

        return CandidatResponse.builder()
                .id(candidat.getId())
                .nom(candidat.getNom())
                .prenom(candidat.getPrenom())
                .nomComplet(candidat.getNomComplet())
                .affiliationOuParti(candidat.getAffiliationOuParti())
                .biographie(candidat.getBiographie())
                .photoUrl(candidat.getPhotoUrl())
                .programmePdfUrl(candidat.getProgrammePdfUrl())
                .electionId(candidat.getElection() != null ? candidat.getElection().getId() : null)
                .nombreVotes(nombreVotes)
                .pourcentageVotes(pourcentage)
                .rang(rang)
                .build();
    }

    // =========================================================
    // Private guard methods
    // =========================================================

    /**
     * Loads an Election entity by ID or throws ResourceNotFoundException.
     */
    private Election loadElectionById(Long electionId) {
        return electionRepository.findById(electionId)
                .orElseThrow(() -> new ResourceNotFoundException("Election", electionId));
    }

    /**
     * Asserts that an election is in a state that allows candidate modification.
     * Only BROUILLON and PLANIFIEE elections can have their candidate list changed.
     *
     * @param election the election to check
     * @throws InvalidElectionStateException if the election is OUVERTE, CLOTUREE, or PUBLIEE
     */
    private void assertElectionIsModifiable(Election election) {
        StatutElection statut = election.getStatut();
        if (statut != StatutElection.BROUILLON && statut != StatutElection.PLANIFIEE) {
            throw new InvalidElectionStateException(
                    "Les candidats ne peuvent être modifiés que lorsque l'élection est en état "
                    + "BROUILLON ou PLANIFIEE. État actuel de l'élection id="
                    + election.getId() + ": " + statut
            );
        }
    }

    /**
     * Asserts that no candidate with the given nom + prenom already exists in the election.
     *
     * @param nom        last name to check
     * @param prenom     first name to check
     * @param electionId the election scope
     * @throws DuplicateResourceException if a duplicate candidate is found
     */
    private void assertNoDuplicateCandidat(String nom, String prenom, Long electionId) {
        if (candidatRepository.existsByNomAndPrenomAndElectionId(nom, prenom, electionId)) {
            throw new DuplicateResourceException(
                    "Un candidat nommé '" + prenom + " " + nom + "' est déjà enregistré "
                    + "dans l'élection id=" + electionId + "."
            );
        }
    }
}
