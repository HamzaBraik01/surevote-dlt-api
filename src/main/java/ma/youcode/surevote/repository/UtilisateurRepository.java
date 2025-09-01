package ma.youcode.surevote.repository;

import ma.youcode.surevote.domain.entity.Utilisateur;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for all Utilisateur subtypes (Administrateur, Electeur, Observateur).
 * Uses Spring Data JPA with SINGLE_TABLE inheritance — all users are queried
 * from the same 'utilisateurs' table with discriminator filtering.
 */
@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    // -----------------------------------------------------------------------
    // Lookup by identity fields
    // -----------------------------------------------------------------------

    /**
     * Find a user by their email address (used as the authentication principal).
     *
     * @param email the unique email address
     * @return an Optional containing the user if found
     */
    Optional<Utilisateur> findByEmail(String email);

    /**
     * Find a user by their national ID number (CIN).
     *
     * @param cin the national ID number
     * @return an Optional containing the user if found
     */
    Optional<Utilisateur> findByCin(String cin);

    // -----------------------------------------------------------------------
    // Existence checks
    // -----------------------------------------------------------------------

    /**
     * Check whether an email address is already registered in the system.
     *
     * @param email the email to check
     * @return true if a user with that email exists
     */
    boolean existsByEmail(String email);

    /**
     * Check whether a CIN is already registered in the system.
     *
     * @param cin the national ID to check
     * @return true if a user with that CIN exists
     */
    boolean existsByCin(String cin);

    // -----------------------------------------------------------------------
    // Role-based queries
    // -----------------------------------------------------------------------

    /**
     * Retrieve all users with a specific role.
     *
     * @param role the role to filter by
     * @return list of matching users
     */
    List<Utilisateur> findAllByRole(RoleUtilisateur role);

    /**
     * Count users by role — useful for dashboard metrics.
     *
     * @param role the role to count
     * @return number of users with that role
     */
    long countByRole(RoleUtilisateur role);

    // -----------------------------------------------------------------------
    // Account management
    // -----------------------------------------------------------------------

    /**
     * Retrieve all active (enabled) users.
     *
     * @return list of enabled users
     */
    List<Utilisateur> findAllByIsEnabledTrue();

    /**
     * Retrieve all disabled (deactivated) users.
     *
     * @return list of disabled users
     */
    List<Utilisateur> findAllByIsEnabledFalse();

    /**
     * Paginated retrieval of all users.
     * The calling service method runs within @Transactional(readOnly = true),
     * keeping the Hibernate session open during DTO mapping to allow
     * lazy loading of Electeur.collegeElectoral.
     *
     * @param pageable pagination and sort parameters
     * @return paged users
     */
    @Override
    Page<Utilisateur> findAll(Pageable pageable);

    /**
     * Activate or deactivate a user account by ID.
     * Uses a direct JPQL UPDATE to avoid loading the full entity.
     *
     * @param id      the user ID
     * @param enabled true to activate, false to deactivate
     */
    @Modifying
    @Query("UPDATE Utilisateur u SET u.isEnabled = :enabled WHERE u.id = :id")
    void updateEnabledStatus(@Param("id") Long id, @Param("enabled") boolean enabled);

    /**
     * Update a user's role by ID.
     * Admin-only operation — no privilege escalation is permitted via the API layer.
     *
     * @param id   the user ID
     * @param role the new role to assign
     */
    @Modifying
    @Query("UPDATE Utilisateur u SET u.role = :role WHERE u.id = :id")
    void updateRole(@Param("id") Long id, @Param("role") RoleUtilisateur role);

    // -----------------------------------------------------------------------
    // Search
    // -----------------------------------------------------------------------

    /**
     * Search users by name (nom or prenom), case-insensitive.
     * Useful for admin user management panels.
     *
     * @param keyword the search keyword
     * @return list of matching users
     */
    @Query("SELECT u FROM Utilisateur u WHERE " +
           "LOWER(u.nom) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(u.prenom) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Utilisateur> searchByKeyword(@Param("keyword") String keyword);
}
