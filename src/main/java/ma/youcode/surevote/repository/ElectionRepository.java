package ma.youcode.surevote.repository;

import ma.youcode.surevote.domain.entity.Election;
import ma.youcode.surevote.domain.enums.StatutElection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for Election entities.
 *
 * Provides standard CRUD plus custom JPQL queries required by:
 *  - The election state-machine scheduler (@Scheduled tasks)
 *  - Admin dashboards (filter by status)
 *  - Voter eligibility checks (open elections accessible to a college)
 *  - Result computation triggers (elections just closed)
 */
@Repository
public interface ElectionRepository extends JpaRepository<Election, Long> {

    // =========================================================
    // State Machine Scheduler Queries
    // Used by ElectionSchedulerService every minute
    // =========================================================

    /**
     * Finds all elections in PLANIFIEE status whose start date has arrived.
     * These elections should transition to OUVERTE.
     *
     * @param now current UTC timestamp
     * @return list of elections ready to be opened
     */
    @Query("""
            SELECT e FROM Election e
            WHERE e.statut = :#{T(ma.youcode.surevote.domain.enums.StatutElection).PLANIFIEE}
              AND e.dateDebut <= :now
            """)
    List<Election> findElectionsToOpen(@Param("now") LocalDateTime now);

    /**
     * Finds all elections in OUVERTE status whose end date has passed.
     * These elections should transition to CLOTUREE.
     *
     * @param now current UTC timestamp
     * @return list of elections ready to be closed
     */
    @Query("""
            SELECT e FROM Election e
            WHERE e.statut = :#{T(ma.youcode.surevote.domain.enums.StatutElection).OUVERTE}
              AND e.dateFin <= :now
            """)
    List<Election> findElectionsToClose(@Param("now") LocalDateTime now);

    // =========================================================
    // Status-Based Filters
    // =========================================================

    /**
     * Returns all elections with a given status.
     * Useful for admin dashboards and filtered listings.
     *
     * @param statut the election status to filter by
     * @return list of elections matching the given status
     */
    List<Election> findAllByStatutOrderByDateDebutDesc(StatutElection statut);

    /**
     * Returns all elections that are currently open (OUVERTE).
     * Used by voters to list available ballots.
     *
     * @return list of open elections
     */
    @Query("""
            SELECT e FROM Election e
            WHERE e.statut = :#{T(ma.youcode.surevote.domain.enums.StatutElection).OUVERTE}
            ORDER BY e.dateFin ASC
            """)
    List<Election> findAllOpenElections();

    /**
     * Returns all elections with published results (PUBLIEE).
     * Used by the public results dashboard.
     *
     * @return list of published elections ordered by most recently closed
     */
    @Query("""
            SELECT e FROM Election e
            WHERE e.statut = :#{T(ma.youcode.surevote.domain.enums.StatutElection).PUBLIEE}
            ORDER BY e.dateFin DESC
            """)
    List<Election> findAllPublishedElections();

    /**
     * Returns all elections visible to voters: OUVERTE or PUBLIEE.
     * BROUILLON and PLANIFIEE are hidden from non-admin users.
     *
     * @return list of visible elections
     */
    @Query("""
            SELECT e FROM Election e
            WHERE e.statut IN (
                :#{T(ma.youcode.surevote.domain.enums.StatutElection).OUVERTE},
                :#{T(ma.youcode.surevote.domain.enums.StatutElection).PLANIFIEE},
                :#{T(ma.youcode.surevote.domain.enums.StatutElection).PUBLIEE}
            )
            ORDER BY e.dateDebut DESC
            """)
    List<Election> findAllVisibleToVoters();

    // =========================================================
    // Electoral College — Voter Eligibility
    // =========================================================

    /**
     * Returns all open elections accessible to a specific voter,
     * considering their electoral college membership.
     *
     * An election is accessible if:
     *   (a) It has no electoral college restriction (open to all), OR
     *   (b) The voter belongs to the election's assigned college.
     *
     * @param electeurId the ID of the voter
     * @return list of elections the voter is eligible to vote in
     */
    @Query("""
            SELECT e FROM Election e
            WHERE e.statut = :#{T(ma.youcode.surevote.domain.enums.StatutElection).OUVERTE}
              AND (
                  e.collegeElectoral IS NULL
                  OR e.collegeElectoral.id IN (
                      SELECT el.collegeElectoral.id FROM Electeur el
                      WHERE el.id = :electeurId
                        AND el.collegeElectoral IS NOT NULL
                  )
              )
            ORDER BY e.dateFin ASC
            """)
    List<Election> findEligibleElectionsForVoter(@Param("electeurId") Long electeurId);

    /**
     * Returns all open elections restricted to a specific college.
     *
     * @param collegeId the college ID to filter by
     * @return open elections restricted to this college
     */
    @Query("""
            SELECT e FROM Election e
            WHERE e.statut = :#{T(ma.youcode.surevote.domain.enums.StatutElection).OUVERTE}
              AND e.collegeElectoral.id = :collegeId
            """)
    List<Election> findOpenElectionsByCollege(@Param("collegeId") Long collegeId);

    // =========================================================
    // Statistics & Metrics
    // =========================================================

    /**
     * Counts elections grouped by status.
     * Used for the admin/observer metrics dashboard.
     *
     * @param statut the election status to count
     * @return number of elections with that status
     */
    long countByStatut(StatutElection statut);

    /**
     * Returns elections whose date range overlaps with the given period.
     * Used to detect scheduling conflicts when creating new elections.
     *
     * @param start the start of the period to check
     * @param end   the end of the period to check
     * @return elections overlapping with the given window
     */
    @Query("""
            SELECT e FROM Election e
            WHERE e.dateDebut < :end
              AND e.dateFin   > :start
              AND e.statut NOT IN (
                  :#{T(ma.youcode.surevote.domain.enums.StatutElection).CLOTUREE},
                  :#{T(ma.youcode.surevote.domain.enums.StatutElection).PUBLIEE}
              )
            """)
    List<Election> findOverlappingElections(@Param("start") LocalDateTime start,
                                             @Param("end")   LocalDateTime end);

    // =========================================================
    // Eager-fetch Queries (avoid N+1 in result computation)
    // =========================================================

    /**
     * Fetches a single election with its candidates eagerly loaded.
     * Used during result computation to avoid N+1 queries.
     *
     * @param id the election ID
     * @return Optional containing the election with candidates, or empty
     */
    @Query("""
            SELECT DISTINCT e FROM Election e
            LEFT JOIN FETCH e.candidats
            WHERE e.id = :id
            """)
    Optional<Election> findByIdWithCandidats(@Param("id") Long id);

    /**
     * Fetches a single election with its collegeElectoral eagerly loaded.
     * Used during result computation to safely access college restrictions
     * when computing eligible voter counts (avoids LazyInitializationException).
     *
     * @param id the election ID
     * @return Optional containing the election with collegeElectoral, or empty
     */
    @Query("""
            SELECT e FROM Election e
            LEFT JOIN FETCH e.collegeElectoral
            WHERE e.id = :id
            """)
    Optional<Election> findByIdWithCollegeElectoral(@Param("id") Long id);

    /**
     * Fetches a single election with its emargements eagerly loaded.
     * Used when computing participation statistics.
     *
     * @param id the election ID
     * @return Optional containing the election with emargements, or empty
     */
    @Query("""
            SELECT DISTINCT e FROM Election e
            LEFT JOIN FETCH e.emargements
            WHERE e.id = :id
            """)
    Optional<Election> findByIdWithEmargements(@Param("id") Long id);

    // =========================================================
    // Bulk Status Update (used by scheduler for performance)
    // =========================================================

    /**
     * Bulk-updates elections from PLANIFIEE to OUVERTE for all elections
     * whose start date has been reached.
     * More efficient than loading entities individually for large datasets.
     *
     * @param now current UTC timestamp
     * @return number of elections updated
     */
    @Modifying
    @Query("""
            UPDATE Election e
            SET e.statut = :#{T(ma.youcode.surevote.domain.enums.StatutElection).OUVERTE}
            WHERE e.statut = :#{T(ma.youcode.surevote.domain.enums.StatutElection).PLANIFIEE}
              AND e.dateDebut <= :now
            """)
    int bulkOpenElections(@Param("now") LocalDateTime now);

    /**
     * Bulk-updates elections from OUVERTE to CLOTUREE for all elections
     * whose end date has been reached.
     *
     * @param now current UTC timestamp
     * @return number of elections updated
     */
    @Modifying
    @Query("""
            UPDATE Election e
            SET e.statut = :#{T(ma.youcode.surevote.domain.enums.StatutElection).CLOTUREE}
            WHERE e.statut = :#{T(ma.youcode.surevote.domain.enums.StatutElection).OUVERTE}
              AND e.dateFin <= :now
            """)
    int bulkCloseElections(@Param("now") LocalDateTime now);

    // =========================================================
    // Search
    // =========================================================

    /**
     * Full-text search across election title and description.
     * Case-insensitive LIKE search.
     *
     * @param keyword the search term
     * @return matching elections
     */
    @Query("""
            SELECT e FROM Election e
            WHERE LOWER(e.titre) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(e.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
            ORDER BY e.dateDebut DESC
            """)
    List<Election> searchByKeyword(@Param("keyword") String keyword);
}
