package ma.youcode.surevote.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.annotation.Auditable;
import ma.youcode.surevote.domain.entity.Administrateur;
import ma.youcode.surevote.domain.entity.Electeur;
import ma.youcode.surevote.domain.entity.Observateur;
import ma.youcode.surevote.domain.entity.Utilisateur;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import ma.youcode.surevote.dto.request.CreateUserRequest;
import ma.youcode.surevote.dto.response.UserResponse;
import ma.youcode.surevote.exception.DuplicateResourceException;
import ma.youcode.surevote.exception.ResourceNotFoundException;
import ma.youcode.surevote.mapper.UtilisateurMapper;
import ma.youcode.surevote.repository.UtilisateurRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service layer for Utilisateur management.
 *
 * Handles all user-related business operations:
 *   - Listing all users (admin view)
 *   - Retrieving individual user profiles
 *   - Updating roles (admin-only)
 *   - Activating / deactivating accounts
 *   - Keyword search
 *   - Mapping entities to safe response DTOs
 *
 * Security note: This service never exposes motDePasse, otpCode, or any
 * credential field through its response DTOs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final UtilisateurMapper utilisateurMapper;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";

    // =========================================================
    // Admin user creation
    // =========================================================

    /**
     * Creates a new user with any role (admin operation).
     * Generates a temporary password if not provided, and sends it by email.
     */
    @Transactional
    @Auditable(actionType = "USER_CREATED_BY_ADMIN", description = "Création d'un utilisateur par l'administrateur")
    public UserResponse createUser(CreateUserRequest request) {
        assertEmailNotTaken(request.getEmail());
        assertCinNotTaken(request.getCin());

        String rawPassword = (Boolean.TRUE.equals(request.getGenerateRandomPassword()) || request.getMotDePasse() == null)
                ? generateTemporaryPassword()
                : request.getMotDePasse();

        String hashedPassword = passwordEncoder.encode(rawPassword);

        Utilisateur user = switch (request.getRole()) {
            case ADMIN -> {
                Administrateur admin = new Administrateur();
                populateBase(admin, request, hashedPassword);
                admin.setDepartement(request.getDepartement());
                yield admin;
            }
            case OBSERVATEUR -> {
                Observateur obs = new Observateur();
                populateBase(obs, request, hashedPassword);
                obs.setOrganisme(request.getOrganisme());
                yield obs;
            }
            default -> {
                Electeur electeur = new Electeur();
                populateBase(electeur, request, hashedPassword);
                electeur.setTelephone(request.getTelephone());
                electeur.setDoubleFacteurActif(Boolean.TRUE.equals(request.getDoubleFacteurActif()));
                yield electeur;
            }
        };

        Utilisateur saved = utilisateurRepository.save(user);
        log.info("User created by admin: id={}, email={}, role={}", saved.getId(), saved.getEmail(), saved.getRole());

        // Send temporary password by email
        if (Boolean.TRUE.equals(request.getGenerateRandomPassword())) {
            notificationService.sendEmail(
                saved.getEmail(),
                "SUREVOTE — Votre compte a été créé",
                "Bonjour " + saved.getPrenom() + ",\n\nVotre compte SUREVOTE a été créé.\n" +
                "Email: " + saved.getEmail() + "\nMot de passe temporaire: " + rawPassword +
                "\n\nVeuillez changer votre mot de passe à la première connexion."
            );
        }

        return toResponse(saved);
    }

    private void populateBase(Utilisateur u, CreateUserRequest req, String hashedPassword) {
        u.setCin(req.getCin().toUpperCase().trim());
        u.setNom(req.getNom().trim());
        u.setPrenom(req.getPrenom().trim());
        u.setEmail(req.getEmail().toLowerCase().trim());
        u.setMotDePasse(hashedPassword);
        u.setRole(req.getRole());
        u.setEnabled(true);
    }

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(PASSWORD_CHARS.charAt(SECURE_RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    // =========================================================
    // Read operations
    // =========================================================

    /**
     * Returns a paginated list of all registered users.
     * Admin-only operation.
     *
     * @return list of all users mapped to UserResponse DTOs
     */
    public List<UserResponse> findAll() {
        log.debug("Fetching all users");
        return utilisateurRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public Page<UserResponse> findAll(Pageable pageable) {
        log.debug("Fetching paged users: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        try {
            return utilisateurRepository.findAll(pageable)
                    .map(this::toResponse);
        } catch (Exception ex) {
            log.error("Error fetching paged users: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * Returns all users filtered by role.
     *
     * @param role the role to filter by
     * @return list of users with the given role
     */
    public List<UserResponse> findAllByRole(RoleUtilisateur role) {
        log.debug("Fetching users with role: {}", role);
        return utilisateurRepository.findAllByRole(role)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Returns all active (enabled) users.
     *
     * @return list of enabled user response DTOs
     */
    public List<UserResponse> findAllActive() {
        return utilisateurRepository.findAllByIsEnabledTrue()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a single user by their internal database ID.
     *
     * @param id the user's primary key
     * @return the user's public profile DTO
     * @throws ResourceNotFoundException if no user exists with the given ID
     */
    public UserResponse findById(Long id) {
        log.debug("Fetching user by id: {}", id);
        Utilisateur user = utilisateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
        return toResponse(user);
    }

    /**
     * Retrieves a single user by their email address.
     *
     * @param email the user's email (= Spring Security principal)
     * @return the user's public profile DTO
     * @throws ResourceNotFoundException if no user exists with the given email
     */
    public UserResponse findByEmail(String email) {
        log.debug("Fetching user by email: {}", email);
        Utilisateur user = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Utilisateur introuvable avec l'email: " + email));
        return toResponse(user);
    }

    /**
     * Returns the currently authenticated user's profile.
     *
     * @return the authenticated user's response DTO
     */
    public UserResponse getCurrentUserProfile() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return findByEmail(email);
    }

    /**
     * Searches users by keyword (nom, prenom, or email), case-insensitive.
     *
     * @param keyword the search term
     * @return matching users as response DTOs
     */
    public List<UserResponse> search(String keyword) {
        log.debug("Searching users with keyword: '{}'", keyword);
        if (keyword == null || keyword.isBlank()) {
            return findAll();
        }
        return utilisateurRepository.searchByKeyword(keyword.trim())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // =========================================================
    // Write operations (admin-only)
    // =========================================================

    /**
     * Updates the role of a user identified by their ID.
     * Admin-only operation. No privilege escalation is permitted via this method —
     * the security layer enforces that only ADMIN users can call this endpoint.
     *
     * @param id      the ID of the user whose role is being updated
     * @param newRole the new role to assign
     * @return the updated user's response DTO
     * @throws ResourceNotFoundException if the user does not exist
     */
    @Transactional
    @Auditable(actionType = "ROLE_UPDATED", description = "Mise à jour du rôle utilisateur")
    public UserResponse updateRole(Long id, RoleUtilisateur newRole) {
        log.info("Updating role for user id={} to {}", id, newRole);

        Utilisateur user = utilisateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));

        if (user.getRole() == newRole) {
            log.debug("Role unchanged for user id={}: already {}", id, newRole);
            return toResponse(user);
        }

        utilisateurRepository.updateRole(id, newRole);
        user.setRole(newRole);

        log.info("Role updated successfully for user id={}: {} → {}", id, user.getRole(), newRole);
        return toResponse(user);
    }

    /**
     * Activates or deactivates a user account.
     * A deactivated account cannot authenticate — Spring Security will reject it.
     *
     * @param id      the ID of the user to activate/deactivate
     * @param enabled true to activate, false to deactivate
     * @return the updated user's response DTO
     * @throws ResourceNotFoundException if the user does not exist
     */
    @Transactional
    @Auditable(actionType = "ACCOUNT_STATUS_UPDATED", description = "Activation/désactivation du compte utilisateur")
    public UserResponse setAccountEnabled(Long id, boolean enabled) {
        log.info("{} account for user id={}",
                enabled ? "Activating" : "Deactivating", id);

        Utilisateur user = utilisateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));

        if (user.isEnabled() == enabled) {
            log.debug("Account status unchanged for user id={}: already {}", id,
                    enabled ? "active" : "inactive");
            return toResponse(user);
        }

        utilisateurRepository.updateEnabledStatus(id, enabled);
        user.setEnabled(enabled);

        log.info("Account {} for user id={} ({})",
                enabled ? "activated" : "deactivated", id, user.getEmail());
        return toResponse(user);
    }

    /**
     * Soft-deletes a user by deactivating their account.
     * Hard deletion is not supported to preserve audit trail integrity.
     *
     * @param id the ID of the user to deactivate
     * @throws ResourceNotFoundException if the user does not exist
     */
    @Transactional
    @Auditable(actionType = "USER_DEACTIVATED", description = "Désactivation du compte utilisateur")
    public void deactivateUser(Long id) {
        log.info("Deactivating user id={}", id);
        setAccountEnabled(id, false);
    }

    // =========================================================
    // Statistics
    // =========================================================

    /**
     * Returns the total number of registered users.
     */
    public long countAll() {
        return utilisateurRepository.count();
    }

    /**
     * Returns the number of users with a given role.
     *
     * @param role the role to count
     * @return count of users with that role
     */
    public long countByRole(RoleUtilisateur role) {
        return utilisateurRepository.countByRole(role);
    }

    /**
     * Returns the number of active (enabled) user accounts.
     */
    public long countActive() {
        return utilisateurRepository.findAllByIsEnabledTrue().size();
    }

    // =========================================================
    // Internal helpers
    // =========================================================

    /**
     * Loads the raw entity for a given ID.
     * Used internally by other services that need the full entity.
     *
     * @param id the user ID
     * @return the Utilisateur entity
     * @throws ResourceNotFoundException if not found
     */
    public Utilisateur findEntityById(Long id) {
        return utilisateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
    }

    /**
     * Maps a Utilisateur entity (and its subtype) to a safe UserResponse DTO.
     * Handles all three subtypes: Administrateur, Electeur, Observateur.
     * Never includes credential or OTP fields.
     *
     * @param user the entity to map
     * @return the mapped UserResponse DTO
     */
    public UserResponse toResponse(Utilisateur user) {
        return utilisateurMapper.toResponse(user);
    }

    /**
     * Validates that an email address is not already taken.
     *
     * @param email the email to check
     * @throws DuplicateResourceException if the email is already registered
     */
    public void assertEmailNotTaken(String email) {
        if (utilisateurRepository.existsByEmail(email)) {
            throw new DuplicateResourceException(
                    "Un compte avec l'email '" + email + "' existe déjà.");
        }
    }

    /**
     * Validates that a CIN is not already registered.
     *
     * @param cin the national ID to check
     * @throws DuplicateResourceException if the CIN is already registered
     */
    public void assertCinNotTaken(String cin) {
        if (utilisateurRepository.existsByCin(cin)) {
            throw new DuplicateResourceException(
                    "Un compte avec le CIN '" + cin + "' existe déjà.");
        }
    }
}
