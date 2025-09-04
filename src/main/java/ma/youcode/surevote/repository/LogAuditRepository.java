package ma.youcode.surevote.repository;

import ma.youcode.surevote.domain.entity.LogAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for the immutable audit trail (LogAudit).
 * Provides read-only query operations — no delete or update methods are exposed.
 * All writes are performed via save() only (append-only pattern).
 */
@Repository
public interface LogAuditRepository extends JpaRepository<LogAudit, Long> {

    // =========================================================
    // Paginated queries for the audit log viewer
    // =========================================================

    /**
     * Returns all audit logs ordered by most recent first, paginated.
     *
     * @param pageable pagination and sorting parameters
     * @return a page of LogAudit records
     */
    Page<LogAudit> findAllByOrderByDateActionDesc(Pageable pageable);

    /**
     * Returns audit logs filtered by action type, ordered by most recent first.
     *
     * @param actionType the category to filter (e.g., "LOGIN_SUCCESS", "VOTE_SUBMITTED")
     * @param pageable   pagination parameters
     * @return a page of matching LogAudit records
     */
    Page<LogAudit> findByActionTypeOrderByDateActionDesc(String actionType, Pageable pageable);

    /**
     * Returns audit logs associated with a specific user (by userId), paginated.
     *
     * @param utilisateurId the ID of the user whose actions are queried
     * @param pageable      pagination parameters
     * @return a page of matching LogAudit records
     */
    Page<LogAudit> findByUtilisateurIdOrderByDateActionDesc(Long utilisateurId, Pageable pageable);

    /**
     * Returns audit logs within a specific time window, ordered by most recent first.
     *
     * @param start    start of the time range (inclusive)
     * @param end      end of the time range (inclusive)
     * @param pageable pagination parameters
     * @return a page of matching LogAudit records
     */
    Page<LogAudit> findByDateActionBetweenOrderByDateActionDesc(
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );

    // =========================================================
    // List queries for export (Observer journal export)
    // =========================================================

    /**
     * Returns all audit logs for export — ordered chronologically.
     * Used by the Observer role to export the full audit journal.
     *
     * @return all log records in ascending chronological order
     */
    List<LogAudit> findAllByOrderByDateActionAsc();

    /**
     * Returns audit logs filtered by action type for targeted export.
     *
     * @param actionType the action type to filter
     * @return matching records in ascending chronological order
     */
    List<LogAudit> findByActionTypeOrderByDateActionAsc(String actionType);

    /**
     * Returns all audit logs for a specific user — for export or investigation.
     *
     * @param utilisateurId the user ID to filter by
     * @return matching records in ascending chronological order
     */
    List<LogAudit> findByUtilisateurIdOrderByDateActionAsc(Long utilisateurId);

    /**
     * Returns all audit logs within a date range — for export.
     *
     * @param start start of the time range (inclusive)
     * @param end   end of the time range (inclusive)
     * @return matching records in ascending chronological order
     */
    List<LogAudit> findByDateActionBetweenOrderByDateActionAsc(
            LocalDateTime start,
            LocalDateTime end
    );

    // =========================================================
    // Advanced / JPQL queries
    // =========================================================

    /**
     * Full-text style search across action type and details fields.
     * Used by the audit log search feature in the admin panel.
     *
     * @param keyword  the search term (case-insensitive substring match)
     * @param pageable pagination parameters
     * @return matching records ordered by most recent first
     */
    @Query("""
            SELECT l FROM LogAudit l
            WHERE LOWER(l.actionType) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(l.details) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(COALESCE(l.utilisateurEmail, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
            ORDER BY l.dateAction DESC
            """)
    Page<LogAudit> searchLogs(@Param("keyword") String keyword, Pageable pageable);

    /**
     * Returns all unique action types present in the audit log.
     * Used to populate filter dropdowns in the admin UI.
     *
     * @return distinct list of action type strings
     */
    @Query("SELECT DISTINCT l.actionType FROM LogAudit l ORDER BY l.actionType ASC")
    List<String> findDistinctActionTypes();

    /**
     * Counts audit log entries per action type — useful for metrics dashboards.
     *
     * @return list of [actionType, count] projections
     */
    @Query("SELECT l.actionType, COUNT(l) FROM LogAudit l GROUP BY l.actionType ORDER BY COUNT(l) DESC")
    List<Object[]> countByActionType();

    /**
     * Returns the most recent N audit entries — useful for real-time monitoring dashboards.
     *
     * @param pageable use PageRequest.of(0, n) to limit results
     * @return the most recent audit records
     */
    @Query("SELECT l FROM LogAudit l ORDER BY l.dateAction DESC")
    Page<LogAudit> findMostRecent(Pageable pageable);

    /**
     * Counts login failure events from a specific IP address within a time window.
     * Used to detect brute-force login attempts (FR-11, NFR-01).
     *
     * @param adresseIp the IP address to investigate
     * @param since     the start of the observation window
     * @return number of LOGIN_FAILURE events from that IP since the given timestamp
     */
    @Query("""
            SELECT COUNT(l) FROM LogAudit l
            WHERE l.actionType = 'LOGIN_FAILURE'
              AND l.adresseIp = :adresseIp
              AND l.dateAction >= :since
            """)
    long countLoginFailuresByIp(
            @Param("adresseIp") String adresseIp,
            @Param("since") LocalDateTime since
    );

    /**
     * Returns all fraud attempt logs for security review.
     *
     * @param pageable pagination parameters
     * @return fraud-related audit records ordered by most recent first
     */
    @Query("""
            SELECT l FROM LogAudit l
            WHERE l.actionType IN ('FRAUD_ATTEMPT', 'DUPLICATE_VOTE_ATTEMPT', 'UNAUTHORIZED_ACCESS')
            ORDER BY l.dateAction DESC
            """)
    Page<LogAudit> findFraudAttempts(Pageable pageable);

    /**
     * Returns all voting-related audit logs for a given election.
     * Searched by matching electionId in the details field.
     * Note: uses LIKE — consider JSON storage for production at scale.
     *
     * @param electionId the election ID to search for in audit details
     * @param pageable   pagination parameters
     * @return vote-related audit entries for the specified election
     */
    @Query("""
            SELECT l FROM LogAudit l
            WHERE l.actionType LIKE '%VOTE%'
              AND l.details LIKE CONCAT('%electionId=', :electionId, '%')
            ORDER BY l.dateAction DESC
            """)
    Page<LogAudit> findVoteAuditsByElection(
            @Param("electionId") Long electionId,
            Pageable pageable
    );

    /**
     * Counts the total number of audit entries since a given date.
     * Used in metrics and health monitoring endpoints.
     *
     * @param since the reference date
     * @return count of log entries created after the given date
     */
    long countByDateActionAfter(LocalDateTime since);
}
