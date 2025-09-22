package ma.youcode.surevote.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.annotation.Auditable;
import ma.youcode.surevote.domain.entity.CollegeElectoral;
import ma.youcode.surevote.domain.entity.Election;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import ma.youcode.surevote.domain.enums.StatutElection;
import ma.youcode.surevote.dto.request.ElectionRequest;
import ma.youcode.surevote.dto.response.ElectionResponse;
import ma.youcode.surevote.exception.InvalidElectionStateException;
import ma.youcode.surevote.exception.ResourceNotFoundException;
import ma.youcode.surevote.mapper.ElectionMapper;
import ma.youcode.surevote.repository.CollegeElectoralRepository;
import ma.youcode.surevote.repository.ElectionRepository;
import ma.youcode.surevote.repository.EmargementRepository;
import ma.youcode.surevote.repository.UtilisateurRepository;
import ma.youcode.surevote.repository.VoteRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service layer for Election management in SUREVOTE.
 *
 * Responsibilities:
 *  - Full CRUD operations for elections (Admin-controlled)
 *  - State machine transitions: BROUILLON → PLANIFIEE → OUVERTE → CLOTUREE → PUBLIEE
 *  - Automated state transitions via @Scheduled tasks (every minute, UTC-based)
 *  - Manual override transitions (ouvrirScrutin, cloturerScrutin, publierResultats)
 *  - Electoral college restriction management
 *  - Mapping Election entities to ElectionResponse DTOs
 *  - Voter-facing queries (eligible elections, open elections)
 *
 * State machine rules (enforced at entity level too):
 *   BROUILLON  → PLANIFIEE  (admin action: planifier)
 *   PLANIFIEE  → OUVERTE    (scheduler when dateDebut reached, or admin override)
 *   OUVERTE    → CLOTUREE   (scheduler when dateFin reached, or admin override)
 *   CLOTUREE   → PUBLIEE    (admin action: publier)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ElectionService {

    private final ElectionRepository electionRepository;
    private final CollegeElectoralRepository collegeElectoralRepository;
    private final EmargementRepository emargementRepository;
    private final VoteRepository voteRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ElectionMapper electionMapper;

    private static final int MIN_ELECTION_DURATION_HOURS = 1;
    private static final int MAX_CANDIDATS = 20;

    // =========================================================
    // CRUD — Create
    // =========================================================

    /**
     * Creates a new election in BROUILLON (draft) state.
     *
     * Validates:
     *  - dateFin must be after dateDebut
     *  - If a collegeElectoralId is provided, the college must exist
     *
     * @param request the election configuration data
     * @return the created election as a response DTO
     * @throws IllegalArgumentException if date validation fails
     * @throws ResourceNotFoundException if the specified college does not exist
     */
    @Transactional
    @Auditable(actionType = "ELECTION_CREATED", description = "Création d'une nouvelle élection")
    public ElectionResponse createElection(ElectionRequest request) {
        validateElectionDates(request.getDateDebut(), request.getDateFin());

        Election election = electionMapper.toEntity(request);
        election.setTitre(election.getTitre().trim());
        election.setStatut(StatutElection.BROUILLON);

        // Attach college if specified
        if (request.getCollegeElectoralId() != null) {
            CollegeElectoral college = collegeElectoralRepository
                    .findById(request.getCollegeElectoralId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "CollegeElectoral", request.getCollegeElectoralId()));
            election.setCollegeElectoral(college);
        }

        Election saved = electionRepository.save(election);
        log.info("Election created: id={}, titre='{}', statut={}", saved.getId(), saved.getTitre(), saved.getStatut());
        return toResponse(saved, false);
    }

    // =========================================================
    // CRUD — Read
    // =========================================================

    /**
     * Returns all elections (admin view — all statuses).
     *
     * @return list of all elections as response DTOs
     */
    public List<ElectionResponse> findAll() {
        return electionRepository.findAll()
                .stream()
                .map(e -> toResponse(e, false))
                .toList();
    }

    public Page<ElectionResponse> findAll(Pageable pageable) {
        return electionRepository.findAll(pageable)
                .map(e -> toResponse(e, false));
    }

    /**
     * Returns all elections visible to voters and observers:
     * PLANIFIEE, OUVERTE, and PUBLIEE — excludes BROUILLON (drafts).
     *
     * @return list of visible elections
     */
    public List<ElectionResponse> findAllVisible() {
        return electionRepository.findAllVisibleToVoters()
                .stream()
                .map(e -> toResponse(e, false))
                .toList();
    }

    /**
     * Returns a single election by its ID, including its candidates list.
     *
     * @param id the election's primary key
     * @return the election with candidates as a response DTO
     * @throws ResourceNotFoundException if no election exists with the given ID
     */
    public ElectionResponse findById(Long id) {
        Election election = electionRepository.findByIdWithCandidats(id)
                .orElseThrow(() -> new ResourceNotFoundException("Election", id));
        return toResponse(election, true);
    }

    /**
     * Returns all elections filtered by a specific status.
     *
     * @param statut the status to filter by
     * @return list of elections with that status
     */
    public List<ElectionResponse> findByStatut(StatutElection statut) {
        return electionRepository.findAllByStatutOrderByDateDebutDesc(statut)
                .stream()
                .map(e -> toResponse(e, false))
                .toList();
    }

    /**
     * Returns all elections that are currently OUVERTE (active ballot period).
     *
     * @return list of open elections
     */
    public List<ElectionResponse> findAllOpen() {
        return electionRepository.findAllOpenElections()
                .stream()
                .map(e -> toResponse(e, false))
                .toList();
    }

    /**
     * Returns all elections with published results (PUBLIEE).
     *
     * @return list of published elections
     */
    public List<ElectionResponse> findAllPublished() {
        return electionRepository.findAllPublishedElections()
                .stream()
                .map(e -> toResponse(e, false))
                .toList();
    }

    /**
     * Returns all elections the given voter is eligible to participate in.
     * Considers college membership and current election status.
     *
     * @param electeurId the voter's ID
     * @return list of eligible open elections for this voter
     */
    public List<ElectionResponse> findEligibleForVoter(Long electeurId) {
        return electionRepository.findEligibleElectionsForVoter(electeurId)
                .stream()
                .map(e -> toResponse(e, false))
                .toList();
    }

    /**
     * Full-text search across election title and description.
     *
     * @param keyword the search term
     * @return matching elections
     */
    public List<ElectionResponse> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return findAllVisible();
        }
        return electionRepository.searchByKeyword(keyword.trim())
                .stream()
                .map(e -> toResponse(e, false))
                .toList();
    }

    // =========================================================
    // CRUD — Update
    // =========================================================

    /**
     * Updates the configuration of an existing election.
     * Only allowed when the election is in BROUILLON or PLANIFIEE state.
     * Once OUVERTE, the election cannot be modified to preserve ballot integrity.
     *
     * @param id      the election ID
     * @param request the new configuration data
     * @return the updated election as a response DTO
     * @throws ResourceNotFoundException     if the election does not exist
     * @throws InvalidElectionStateException if the election is not in BROUILLON or PLANIFIEE
     */
    @Transactional
    @Auditable(actionType = "ELECTION_UPDATED", description = "Mise à jour de la configuration de l'élection")
    public ElectionResponse updateElection(Long id, ElectionRequest request) {
        Election election = loadElectionOrThrow(id);

        // Modification lock: cannot edit an active or closed election
        if (election.getStatut() == StatutElection.OUVERTE
                || election.getStatut() == StatutElection.CLOTUREE
                || election.getStatut() == StatutElection.PUBLIEE) {
            throw new InvalidElectionStateException(
                    "Impossible de modifier l'élection '" + election.getTitre() +
                    "' dans l'état actuel: " + election.getStatut() +
                    ". Seules les élections BROUILLON ou PLANIFIEE peuvent être modifiées."
            );
        }

        validateElectionDates(request.getDateDebut(), request.getDateFin());

        electionMapper.updateEntity(request, election);
        election.setTitre(election.getTitre().trim());

        // Update college restriction
        if (request.getCollegeElectoralId() != null) {
            CollegeElectoral college = collegeElectoralRepository
                    .findById(request.getCollegeElectoralId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "CollegeElectoral", request.getCollegeElectoralId()));
            election.setCollegeElectoral(college);
        } else {
            election.setCollegeElectoral(null);
        }

        Election updated = electionRepository.save(election);
        log.info("Election updated: id={}, titre='{}'", updated.getId(), updated.getTitre());
        return toResponse(updated, true);
    }

    // =========================================================
    // CRUD — Delete
    // =========================================================

    /**
     * Deletes an election permanently.
     * Only allowed when the election is in BROUILLON state.
     * Elections that have received votes cannot be deleted.
     *
     * @param id the election ID to delete
     * @throws ResourceNotFoundException     if the election does not exist
     * @throws InvalidElectionStateException if the election is not in BROUILLON
     */
    @Transactional
    @Auditable(actionType = "ELECTION_DELETED", description = "Suppression d'une élection en brouillon")
    public void deleteElection(Long id) {
        Election election = loadElectionOrThrow(id);

        if (election.getStatut() != StatutElection.BROUILLON) {
            throw new InvalidElectionStateException(
                    "Impossible de supprimer l'élection '" + election.getTitre() +
                    "'. Seules les élections en état BROUILLON peuvent être supprimées. " +
                    "État actuel: " + election.getStatut()
            );
        }

        // Additional safety: block if votes already exist (shouldn't happen in BROUILLON, but guard anyway)
        if (voteRepository.existsByElectionId(id)) {
            throw new InvalidElectionStateException(
                    "Impossible de supprimer l'élection: des bulletins de vote ont déjà été enregistrés."
            );
        }

        electionRepository.deleteById(id);
        log.info("Election deleted: id={}, titre='{}'", id, election.getTitre());
    }

    // =========================================================
    // State Machine — Manual Admin Transitions
    // =========================================================

    /**
     * Transitions an election from BROUILLON → PLANIFIEE.
     * Prerequisites: election must have at least 2 candidates.
     *
     * @param id the election ID
     * @return the updated election response DTO
     */
    @Transactional
    @Auditable(actionType = "ELECTION_PLANNED", description = "Élection planifiée (BROUILLON → PLANIFIEE)")
    public ElectionResponse planifierElection(Long id) {
        Election election = loadElectionOrThrow(id);

        if (election.getStatut() != StatutElection.BROUILLON) {
            throw new InvalidElectionStateException(
                    "La transition PLANIFIER requiert l'état BROUILLON. État actuel: " + election.getStatut()
            );
        }

        if (election.getCandidats() == null || election.getCandidats().size() < 2) {
            throw new InvalidElectionStateException(
                    "L'élection doit avoir au moins 2 candidats avant d'être planifiée."
            );
        }

        if (election.getCandidats().size() > MAX_CANDIDATS) {
            throw new InvalidElectionStateException(
                    "L'élection ne peut pas avoir plus de " + MAX_CANDIDATS + " candidats."
            );
        }

        election.planifier();
        Election updated = electionRepository.save(election);
        log.info("Election planned (BROUILLON→PLANIFIEE): id={}", id);
        return toResponse(updated, false);
    }

    /**
     * Manually opens an election: PLANIFIEE → OUVERTE.
     * Allows early opening before the scheduled dateDebut.
     *
     * @param id the election ID
     * @return the updated election response DTO
     */
    @Transactional
    @Auditable(actionType = "ELECTION_OPENED", description = "Ouverture manuelle du scrutin (PLANIFIEE → OUVERTE)")
    public ElectionResponse ouvrirScrutin(Long id) {
        Election election = loadElectionOrThrow(id);

        if (election.getStatut() != StatutElection.PLANIFIEE) {
            throw new InvalidElectionStateException(
                    "La transition OUVRIR requiert l'état PLANIFIEE. État actuel: " + election.getStatut()
            );
        }

        election.ouvrir();
        Election updated = electionRepository.save(election);
        log.info("Election manually opened (PLANIFIEE→OUVERTE): id={}", id);
        return toResponse(updated, false);
    }

    /**
     * Manually closes an election: OUVERTE → CLOTUREE.
     * Allows early closure before the scheduled dateFin.
     * Results are computed immediately after this transition.
     *
     * @param id the election ID
     * @return the updated election response DTO
     */
    @Transactional
    @Auditable(actionType = "ELECTION_CLOSED", description = "Clôture manuelle du scrutin (OUVERTE → CLOTUREE)")
    public ElectionResponse cloturerScrutin(Long id) {
        Election election = loadElectionOrThrow(id);

        if (election.getStatut() != StatutElection.OUVERTE) {
            throw new InvalidElectionStateException(
                    "La transition CLOTURER requiert l'état OUVERTE. État actuel: " + election.getStatut()
            );
        }

        election.cloturer();
        Election updated = electionRepository.save(election);
        log.info("Election manually closed (OUVERTE→CLOTUREE): id={}", id);
        return toResponse(updated, false);
    }

    /**
     * Publishes the election results: CLOTUREE → PUBLIEE.
     * Results become publicly accessible after this transition.
     *
     * @param id the election ID
     * @return the updated election response DTO
     */
    @Transactional
    @Auditable(actionType = "ELECTION_PUBLISHED", description = "Publication des résultats (CLOTUREE → PUBLIEE)")
    public ElectionResponse publierResultats(Long id) {
        Election election = loadElectionOrThrow(id);

        if (election.getStatut() != StatutElection.CLOTUREE) {
            throw new InvalidElectionStateException(
                    "La transition PUBLIER requiert l'état CLOTUREE. État actuel: " + election.getStatut()
            );
        }

        election.publier();
        Election updated = electionRepository.save(election);
        log.info("Election results published (CLOTUREE→PUBLIEE): id={}", id);
        return toResponse(updated, false);
    }

    // =========================================================
    // Internal entity loader
    // =========================================================

    /**
     * Loads an Election entity by ID, throwing a ResourceNotFoundException if absent.
     * Used internally — returns the raw entity, not a DTO.
     *
     * @param id the election's primary key
     * @return the Election entity
     * @throws ResourceNotFoundException if not found
     */
    public Election loadElectionOrThrow(Long id) {
        return electionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Election", id));
    }

    // =========================================================
    // Statistics
    // =========================================================

    /**
     * Returns the count of elections per status — for metrics dashboards.
     *
     * @param statut the status to count
     * @return number of elections with that status
     */
    public long countByStatut(StatutElection statut) {
        return electionRepository.countByStatut(statut);
    }

    /**
     * Returns the total number of elections.
     *
     * @return total election count
     */
    public long countAll() {
        return electionRepository.count();
    }

    // =========================================================
    // DTO Mapping
    // =========================================================

    /**
     * Maps an Election entity to an ElectionResponse DTO.
     *
     * @param election         the source entity
     * @param includeCandidats whether to include the candidate list in the response
     * @return the mapped response DTO
     */
    public ElectionResponse toResponse(Election election, boolean includeCandidats) {
        ElectionResponse response = includeCandidats
                ? electionMapper.toResponseWithCandidats(election)
                : electionMapper.toResponse(election);

        // totalCandidats is safe to expose; compute here to avoid mapping lazy collections elsewhere
        response.setTotalCandidats(election.getCandidats() != null ? election.getCandidats().size() : 0);

        // Participation metrics (only for CLOTUREE and PUBLIEE)
        if (election.getStatut() == StatutElection.CLOTUREE
                || election.getStatut() == StatutElection.PUBLIEE) {

            long totalVotes = voteRepository.countByElectionId(election.getId());
            long totalParticipants = emargementRepository.countByElection_Id(election.getId());

            // Determine total eligible voters
            long totalEligibles;
            if (election.getCollegeElectoral() != null) {
                totalEligibles = collegeElectoralRepository
                        .countMembersByCollegeId(election.getCollegeElectoral().getId());
            } else {
                totalEligibles = utilisateurRepository.countByRole(RoleUtilisateur.ELECTEUR);
            }

            double tauxParticipation = (totalEligibles > 0)
                    ? Math.round(((double) totalParticipants / totalEligibles) * 10000.0) / 100.0
                    : 0.0;

            response.setTotalVotes(totalVotes);
            response.setTotalParticipants(totalParticipants);
            response.setTotalElecteursEligibles(totalEligibles);
            response.setTauxParticipation(tauxParticipation);
        }

        return response;
    }

    // =========================================================
    // Private helpers
    // =========================================================

    /**
     * Validates that the election date range is logically consistent.
     *
     * @param dateDebut the proposed start date
     * @param dateFin   the proposed end date
     * @throws IllegalArgumentException if dateFin is not strictly after dateDebut
     */
    private void validateElectionDates(LocalDateTime dateDebut, LocalDateTime dateFin) {
        if (dateDebut == null || dateFin == null) {
            throw new IllegalArgumentException("Les dates de début et de fin sont obligatoires.");
        }
        if (!dateFin.isAfter(dateDebut)) {
            throw new IllegalArgumentException(
                "La date de fin doit être postérieure à la date de début."
            );
        }
        if (dateDebut.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("La date de début doit être dans le futur.");
        }
        long durationHours = java.time.Duration.between(dateDebut, dateFin).toHours();
        if (durationHours < MIN_ELECTION_DURATION_HOURS) {
            throw new IllegalArgumentException(
                "La durée minimale d'une élection est de " + MIN_ELECTION_DURATION_HOURS + " heure(s)."
            );
        }
    }
}
