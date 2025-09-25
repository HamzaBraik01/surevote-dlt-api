package ma.youcode.surevote.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.annotation.Auditable;
import ma.youcode.surevote.domain.entity.CollegeElectoral;
import ma.youcode.surevote.domain.entity.Electeur;
import ma.youcode.surevote.domain.entity.Utilisateur;
import ma.youcode.surevote.dto.request.CollegeRequest;
import ma.youcode.surevote.dto.response.CollegeResponse;
import ma.youcode.surevote.dto.response.UserResponse;
import ma.youcode.surevote.exception.DuplicateResourceException;
import ma.youcode.surevote.exception.ResourceNotFoundException;
import ma.youcode.surevote.mapper.CollegeElectoralMapper;
import ma.youcode.surevote.repository.CollegeElectoralRepository;
import ma.youcode.surevote.repository.UtilisateurRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service layer for CollegeElectoral (Electoral College) management.
 *
 * An electoral college is a named group of voters that can be assigned
 * to one or more elections to restrict ballot access to eligible members only.
 *
 * Responsibilities:
 *  - CRUD operations on CollegeElectoral entities
 *  - Assigning and removing voters (Electeurs) from colleges
 *  - Validating college uniqueness and membership constraints
 *  - Mapping entities to safe response DTOs
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CollegeService {

    private final CollegeElectoralRepository collegeElectoralRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final UserService userService;
    private final CollegeElectoralMapper collegeElectoralMapper;

    // =========================================================
    // Read operations
    // =========================================================

    /**
     * Returns all electoral colleges on the platform.
     *
     * @return list of all colleges as response DTOs
     */
    public List<CollegeResponse> findAll() {
        log.debug("Fetching all electoral colleges");
        return collegeElectoralRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public Page<CollegeResponse> findAll(Pageable pageable) {
        log.debug("Fetching paged colleges: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        return collegeElectoralRepository.findAll(pageable)
                .map(this::toResponse);
    }

    /**
     * Retrieves a single college by its internal database ID.
     *
     * @param id the college's primary key
     * @return the college's response DTO
     * @throws ResourceNotFoundException if no college exists with the given ID
     */
    public CollegeResponse findById(Long id) {
        log.debug("Fetching college by id: {}", id);
        CollegeElectoral college = findEntityById(id);
        return toResponse(college);
    }

    /**
     * Returns all voters (Electeurs) belonging to a specific college.
     *
     * @param collegeId the ID of the college
     * @return list of voter response DTOs (without credential fields)
     * @throws ResourceNotFoundException if the college does not exist
     */
    public List<UserResponse> findMembersByCollegeId(Long collegeId) {
        log.debug("Fetching members of college id: {}", collegeId);
        CollegeElectoral college = findEntityById(collegeId);

        return college.getElecteurs()
                .stream()
                .map(userService::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Searches colleges by keyword in their name field (case-insensitive).
     *
     * @param keyword the search term
     * @return list of matching colleges as response DTOs
     */
    public List<CollegeResponse> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return findAll();
        }
        return collegeElectoralRepository.findByNomContainingIgnoreCase(keyword.trim())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Returns the total number of voters in a specific college.
     *
     * @param collegeId the college ID
     * @return the member count
     */
    public long countMembers(Long collegeId) {
        return collegeElectoralRepository.countMembersByCollegeId(collegeId);
    }

    // =========================================================
    // Write operations
    // =========================================================

    /**
     * Creates a new electoral college.
     *
     * The college name must be unique across the platform.
     * Duplicate names are rejected with a 409 Conflict.
     *
     * @param request the creation form data
     * @return the newly created college as a response DTO
     * @throws DuplicateResourceException if a college with the same name already exists
     */
    @Transactional
    @Auditable(actionType = "COLLEGE_CREATED", description = "Nouveau collège électoral créé")
    public CollegeResponse create(CollegeRequest request) {
        log.info("Creating electoral college: '{}'", request.getNom());

        if (collegeElectoralRepository.existsByNom(request.getNom())) {
            throw new DuplicateResourceException(
                    "Un collège électoral avec le nom '" + request.getNom() + "' existe déjà."
            );
        }

        CollegeElectoral college = collegeElectoralMapper.toEntity(request);
        college.setNom(college.getNom().trim());
        if (college.getDescription() != null) {
            college.setDescription(college.getDescription().trim());
        }

        CollegeElectoral saved = collegeElectoralRepository.save(college);
        log.info("Electoral college created: id={}, nom='{}'", saved.getId(), saved.getNom());
        return toResponse(saved);
    }

    /**
     * Updates an existing electoral college's name and/or description.
     *
     * If the name is being changed, the new name must not conflict with
     * an existing college (uniqueness check).
     *
     * @param id      the ID of the college to update
     * @param request the updated data
     * @return the updated college as a response DTO
     * @throws ResourceNotFoundException  if no college exists with the given ID
     * @throws DuplicateResourceException if the new name conflicts with another college
     */
    @Transactional
    @Auditable(actionType = "COLLEGE_UPDATED", description = "Mise à jour du collège électoral")
    public CollegeResponse update(Long id, CollegeRequest request) {
        log.info("Updating college id={} to nom='{}'", id, request.getNom());

        CollegeElectoral college = findEntityById(id);

        // Check name uniqueness only if name is actually changing
        if (!college.getNom().equalsIgnoreCase(request.getNom())
                && collegeElectoralRepository.existsByNom(request.getNom())) {
            throw new DuplicateResourceException(
                    "Un collège électoral avec le nom '" + request.getNom() + "' existe déjà."
            );
        }

        collegeElectoralMapper.updateEntity(request, college);
        college.setNom(college.getNom().trim());
        if (college.getDescription() != null) {
            college.setDescription(college.getDescription().trim());
        }

        CollegeElectoral updated = collegeElectoralRepository.save(college);
        log.info("College updated: id={}, nom='{}'", updated.getId(), updated.getNom());
        return toResponse(updated);
    }

    /**
     * Deletes an electoral college by ID.
     *
     * Before deletion:
     *  - All member voters are unlinked from the college (their collegeElectoral is set to null).
     *  - All linked elections have their collegeElectoral reference cleared.
     *
     * Note: Elections in OUVERTE state cannot be unlinked — the admin must close them first.
     *
     * @param id the ID of the college to delete
     * @throws ResourceNotFoundException if no college exists with the given ID
     */
    @Transactional
    @Auditable(actionType = "COLLEGE_DELETED", description = "Suppression du collège électoral")
    public void delete(Long id) {
        log.info("Deleting college id={}", id);
        CollegeElectoral college = findEntityById(id);

        // Block deletion if any linked election is OUVERTE or PLANIFIEE
        boolean hasActiveElection = college.getElections().stream()
                .anyMatch(e -> e.getStatut() == ma.youcode.surevote.domain.enums.StatutElection.OUVERTE
                            || e.getStatut() == ma.youcode.surevote.domain.enums.StatutElection.PLANIFIEE);
        if (hasActiveElection) {
            throw new ma.youcode.surevote.exception.InvalidElectionStateException(
                "Impossible de supprimer le collège '" + college.getNom() +
                "' : une élection active (OUVERTE ou PLANIFIEE) lui est associée. " +
                "Clôturez ou dissociez l'élection avant de supprimer ce collège."
            );
        }

        // Unlink all voters from this college
        college.getElecteurs().forEach(electeur -> electeur.setCollegeElectoral(null));

        // Unlink all elections from this college
        college.getElections().forEach(election -> election.setCollegeElectoral(null));

        collegeElectoralRepository.delete(college);
        log.info("College deleted: id={}, nom='{}'", id, college.getNom());
    }

    // =========================================================
    // Voter membership management
    // =========================================================

    /**
     * Adds a voter (Electeur) to an electoral college.
     *
     * A voter can only belong to one college at a time.
     * If the voter is already in this college, the operation is idempotent.
     * If the voter is in a different college, they are moved to the new one.
     *
     * @param collegeId  the ID of the target college
     * @param electeurId the ID of the voter to add
     * @return the updated college response DTO
     * @throws ResourceNotFoundException if the college or voter does not exist
     * @throws IllegalArgumentException  if the specified user is not an ELECTEUR
     */
    @Transactional
    @Auditable(actionType = "VOTER_ADDED_TO_COLLEGE", description = "Électeur ajouté au collège électoral")
    public CollegeResponse addVoterToCollege(Long collegeId, Long electeurId) {
        log.info("Adding voter id={} to college id={}", electeurId, collegeId);

        CollegeElectoral college = findEntityById(collegeId);

        Utilisateur utilisateur = utilisateurRepository.findById(electeurId)
                .orElseThrow(() -> new ResourceNotFoundException("Electeur", electeurId));

        if (!(utilisateur instanceof Electeur electeur)) {
            throw new IllegalArgumentException(
                    "L'utilisateur id=" + electeurId + " n'est pas un électeur (rôle: " +
                    utilisateur.getRole() + "). Seuls les électeurs peuvent rejoindre un collège."
            );
        }

        // Idempotency check
        if (electeur.getCollegeElectoral() != null
                && electeur.getCollegeElectoral().getId().equals(collegeId)) {
            log.debug("Voter id={} is already in college id={} — no change needed", electeurId, collegeId);
            return toResponse(college);
        }

        // Remove from previous college if applicable
        if (electeur.getCollegeElectoral() != null) {
            CollegeElectoral previousCollege = electeur.getCollegeElectoral();
            previousCollege.getElecteurs().remove(electeur);
            collegeElectoralRepository.save(previousCollege);
            log.debug("Voter id={} removed from previous college id={}", electeurId, previousCollege.getId());
        }

        // Assign to new college
        electeur.setCollegeElectoral(college);
        college.getElecteurs().add(electeur);
        utilisateurRepository.save(electeur);
        CollegeElectoral updated = collegeElectoralRepository.save(college);

        log.info("Voter id={} successfully added to college id={}", electeurId, collegeId);
        return toResponse(updated);
    }

    /**
     * Removes a voter (Electeur) from an electoral college.
     *
     * If the voter is not in any college or not in the specified college,
     * the operation is a no-op (idempotent).
     *
     * @param collegeId  the ID of the college
     * @param electeurId the ID of the voter to remove
     * @return the updated college response DTO
     * @throws ResourceNotFoundException if the college or voter does not exist
     */
    @Transactional
    @Auditable(actionType = "VOTER_REMOVED_FROM_COLLEGE", description = "Électeur retiré du collège électoral")
    public CollegeResponse removeVoterFromCollege(Long collegeId, Long electeurId) {
        log.info("Removing voter id={} from college id={}", electeurId, collegeId);

        CollegeElectoral college = findEntityById(collegeId);

        Utilisateur utilisateur = utilisateurRepository.findById(electeurId)
                .orElseThrow(() -> new ResourceNotFoundException("Electeur", electeurId));

        if (!(utilisateur instanceof Electeur electeur)) {
            throw new IllegalArgumentException(
                    "L'utilisateur id=" + electeurId + " n'est pas un électeur."
            );
        }

        // Only remove if the voter actually belongs to this college
        if (electeur.getCollegeElectoral() == null
                || !electeur.getCollegeElectoral().getId().equals(collegeId)) {
            log.debug("Voter id={} does not belong to college id={} — no change needed", electeurId, collegeId);
            return toResponse(college);
        }

        electeur.setCollegeElectoral(null);
        college.getElecteurs().remove(electeur);
        utilisateurRepository.save(electeur);
        CollegeElectoral updated = collegeElectoralRepository.save(college);

        log.info("Voter id={} successfully removed from college id={}", electeurId, collegeId);
        return toResponse(updated);
    }

    /**
     * Checks whether a specific voter belongs to a specific college.
     *
     * @param collegeId  the college ID
     * @param electeurId the voter ID
     * @return true if the voter is a member of the college
     */
    public boolean isVoterInCollege(Long collegeId, Long electeurId) {
        return collegeElectoralRepository.isElecteurInCollege(collegeId, electeurId);
    }

    // =========================================================
    // Internal helpers
    // =========================================================

    /**
     * Loads the raw CollegeElectoral entity for a given ID.
     * Used internally by other services (e.g., ElectionService).
     *
     * @param id the college's primary key
     * @return the CollegeElectoral entity
     * @throws ResourceNotFoundException if not found
     */
    public CollegeElectoral findEntityById(Long id) {
        return collegeElectoralRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CollegeElectoral", id));
    }

    /**
     * Maps a CollegeElectoral entity to a CollegeResponse DTO.
     *
     * @param college the entity to map
     * @return the mapped CollegeResponse
     */
    public CollegeResponse toResponse(CollegeElectoral college) {
        return collegeElectoralMapper.toResponse(college);
    }
}
