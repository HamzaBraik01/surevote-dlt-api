package ma.youcode.surevote.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.dto.response.AuditLogResponse;
import ma.youcode.surevote.dto.response.MetricsResponse;
import ma.youcode.surevote.service.AuditLogService;
import ma.youcode.surevote.service.MetricsService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;


/**
 * REST controller for Observer (and Admin) read-only access to platform metrics
 * and the immutable audit trail in the SUREVOTE platform.
 *
 * Base path: /api/observer
 * Required roles: OBSERVATEUR or ADMIN
 *
 * Exposes endpoints for:
 *  - Aggregated platform-wide metrics (elections, votes, participation rates)
 *  - Paginated audit log viewer with filtering
 *  - Full audit journal export for independent verification
 *
 * Security guarantees:
 *  - No voter identity is exposed through any endpoint.
 *  - No individual ballot content is exposed.
 *  - All data is strictly aggregated or anonymized.
 *  - The audit log endpoint is read-only — no modification is possible.
 */
@RestController
@RequestMapping("/api/observer")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'OBSERVATEUR')")
@Tag(
    name = "Observer — Metrics & Audit",
    description = "Read-only endpoints for aggregated platform metrics and the immutable audit trail. " +
                  "Accessible by ADMIN and OBSERVATEUR roles."
)
@SecurityRequirement(name = "Bearer Authentication")
public class ObserverController {

    private final MetricsService   metricsService;
    private final AuditLogService  auditLogService;

    // =========================================================
    // GET /api/observer/metrics — Full platform metrics
    // =========================================================

    /**
     * Returns a comprehensive snapshot of all platform-wide metrics.
     *
     * Includes:
     *  - User counts by role and account status
     *  - Election counts by lifecycle status
     *  - Total vote and participation counts
     *  - Average participation rate across all closed elections
     *  - Per-election participation summaries
     *  - Audit trail statistics (login failures, fraud attempts)
     *
     * All data is strictly anonymized — no voter identity is included.
     *
     * GET /api/observer/metrics
     *
     * @return 200 OK with a fully populated MetricsResponse
     */
    @GetMapping("/metrics")
    @Operation(
        summary = "Get full platform metrics",
        description = """
            Returns a comprehensive snapshot of aggregated platform statistics.

            **Includes:**
            - User counts by role (ADMIN, ELECTEUR, OBSERVATEUR) and account status
            - Election counts by lifecycle status (BROUILLON, PLANIFIEE, OUVERTE, CLOTUREE, PUBLIEE)
            - Total votes cast, emargements, and participation rates
            - Per-election participation summaries (closed/published elections only)
            - Audit log statistics (entries in last 24h, login failures, fraud attempts)

            **Security:** All data is strictly anonymized. No voter identity or ballot content is exposed.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Metrics computed and returned successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied — ADMIN or OBSERVATEUR role required")
    })
    public ResponseEntity<MetricsResponse> getFullMetrics() {
        log.debug("GET /api/observer/metrics — full metrics requested");
        MetricsResponse metrics = metricsService.computeFullMetrics();
        return ResponseEntity.ok(metrics);
    }

    // =========================================================
    // GET /api/observer/metrics/summary — Lightweight summary
    // =========================================================

    /**
     * Returns a lightweight summary of the most critical platform metrics.
     * Faster to compute than the full metrics — suitable for dashboard header cards.
     *
     * GET /api/observer/metrics/summary
     *
     * @return 200 OK with a partial MetricsResponse containing key counters only
     */
    @GetMapping("/metrics/summary")
    @Operation(
        summary = "Get lightweight metrics summary",
        description = "Returns a fast summary of the most important platform metrics. " +
                      "Use this endpoint for dashboard header cards that require quick load times."
    )
    @ApiResponse(responseCode = "200", description = "Summary metrics returned")
    public ResponseEntity<MetricsResponse> getSummaryMetrics() {
        log.debug("GET /api/observer/metrics/summary");
        MetricsResponse summary = metricsService.computeSummaryMetrics();
        return ResponseEntity.ok(summary);
    }

    // =========================================================
    // GET /api/observer/metrics/participation — Participation breakdown
    // =========================================================

    /**
     * Returns the per-election participation rate breakdown.
     * Only includes closed (CLOTUREE) and published (PUBLIEE) elections.
     *
     * GET /api/observer/metrics/participation
     *
     * @return 200 OK with list of ElectionParticipationSummary DTOs
     */
    @GetMapping("/metrics/participation")
    @Operation(
        summary = "Get per-election participation breakdown",
        description = "Returns participation rate details for each closed or published election. " +
                      "Ordered by most recently closed first."
    )
    @ApiResponse(responseCode = "200", description = "Participation summaries returned")
    public ResponseEntity<List<MetricsResponse.ElectionParticipationSummary>> getParticipationBreakdown() {
        log.debug("GET /api/observer/metrics/participation");
        List<MetricsResponse.ElectionParticipationSummary> summaries =
                metricsService.buildParticipationSummaries();
        return ResponseEntity.ok(summaries);
    }

    // =========================================================
    // GET /api/observer/audit-logs — Paginated audit log viewer
    // =========================================================

    /**
     * Returns a paginated view of the immutable audit trail.
     *
     * Supports filtering by:
     *  - actionType (e.g., "LOGIN_SUCCESS", "VOTE_SUBMITTED", "ELECTION_CREATED")
     *  - keyword (full-text search across actionType and details)
     *  - dateFrom / dateTo (date range filter)
     *
     * Records are ordered by most recent first by default.
     *
     * GET /api/observer/audit-logs?page=0&size=20&actionType=LOGIN_FAILURE
     *
     * @param page       zero-based page number (default: 0)
     * @param size       page size (default: 20, max: 100)
     * @param actionType optional filter by action type category
     * @param keyword    optional full-text search keyword
     * @return 200 OK with paginated audit log response
     */
    @GetMapping("/audit-logs")
    @Operation(
        summary = "View paginated audit trail",
        description = """
            Returns a paginated view of the immutable audit trail.

            **Filtering options:**
            - `actionType`: Filter by action category (e.g., `LOGIN_SUCCESS`, `VOTE_SUBMITTED`)
            - `keyword`: Full-text search across actionType and details fields

            **Ordering:** Most recent entries first (dateAction DESC).

            **Security:** Audit logs contain action metadata only — no vote choices or passwords.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Audit logs returned successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied — ADMIN or OBSERVATEUR role required")
    })
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Number of records per page (max 100)", example = "20")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Filter by action type category", example = "LOGIN_FAILURE")
            @RequestParam(required = false) String actionType,

            @Parameter(description = "Full-text search keyword (matches actionType and details)")
            @RequestParam(required = false) String keyword) {

        // Clamp size to prevent excessively large queries
        int clampedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, clampedSize, Sort.by("dateAction").descending());

        log.debug("GET /api/observer/audit-logs — page={}, size={}, actionType={}, keyword='{}'",
                page, clampedSize, actionType, keyword);

        Page<AuditLogResponse> result;

        if (keyword != null && !keyword.isBlank()) {
            result = auditLogService.searchLogs(keyword.trim(), pageable);
        } else if (actionType != null && !actionType.isBlank()) {
            result = auditLogService.getLogsByActionType(actionType.trim(), pageable);
        } else {
            result = auditLogService.getAllLogs(pageable);
        }

        return ResponseEntity.ok(result);
    }

    // =========================================================
    // GET /api/observer/audit-logs/{id} — Single audit log entry
    // =========================================================

    /**
     * Returns a single audit log entry by its internal ID.
     * Useful for drilling into a specific event during an investigation.
     *
     * GET /api/observer/audit-logs/{id}
     *
     * @param id the audit log entry's primary key
     * @return 200 OK with the audit log entry, or 404 if not found
     */
    @GetMapping("/audit-logs/{id}")
    @Operation(
        summary = "Get audit log entry by ID",
        description = "Returns a single audit trail entry by its internal database ID."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Audit log entry found"),
        @ApiResponse(responseCode = "404", description = "Audit log entry not found")
    })
    public ResponseEntity<AuditLogResponse> getAuditLogById(
            @Parameter(description = "Audit log entry ID", required = true)
            @PathVariable Long id) {

        log.debug("GET /api/observer/audit-logs/{}", id);

        return auditLogService.getLogById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // =========================================================
    // GET /api/observer/export-logs — Full audit journal export
    // =========================================================

    /**
     * Exports the full audit trail as a list for independent verification.
     *
     * Returns all audit log entries in ascending chronological order
     * (oldest first) for sequential analysis and independent verification
     * of the electoral process.
     *
     * Supports optional actionType filtering to export only specific
     * categories of events (e.g., all vote submissions, all login failures).
     *
     * GET /api/observer/export-logs
     * GET /api/observer/export-logs?actionType=VOTE_SUBMITTED
     *
     * @param actionType optional filter — if provided, only this action type is exported
     * @return 200 OK with the full (or filtered) audit journal as a list
     */
    @GetMapping("/export-logs")
    @Operation(
        summary = "Export full audit journal for independent verification",
        description = """
            Returns the complete audit journal as a flat list, ordered chronologically
            (oldest first) for sequential independent verification.

            **Use case:** Observers export this data to run their own integrity checks,
            cross-reference participation counts, or produce official audit reports.

            **Optional filter:** Provide `actionType` to export only specific event categories.

            **Security:** Exported data contains only action metadata (timestamps, types, IP addresses).
            No vote choices, passwords, or sensitive PII is included.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Audit journal exported successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied — ADMIN or OBSERVATEUR role required")
    })
    public ResponseEntity<List<AuditLogResponse>> exportAuditLogs(
            @Parameter(description = "Optional action type filter for targeted export",
                       example = "VOTE_SUBMITTED")
            @RequestParam(required = false) String actionType) {

        log.info("GET /api/observer/export-logs — journal export requested, actionType='{}'", actionType);

        List<AuditLogResponse> logs;

        if (actionType != null && !actionType.isBlank()) {
            logs = auditLogService.exportLogsByActionType(actionType.trim());
        } else {
            logs = auditLogService.exportAllLogs();
        }

        log.info("Audit journal export: {} entries returned (actionType='{}')", logs.size(), actionType);
        return ResponseEntity.ok(logs);
    }

    // =========================================================
    // GET /api/observer/audit-logs/types — Distinct action types
    // =========================================================

    /**
     * Returns all distinct action type categories present in the audit log.
     * Used to populate filter dropdowns in the observer UI.
     *
     * GET /api/observer/audit-logs/types
     *
     * @return 200 OK with a list of distinct action type strings
     */
    @GetMapping("/audit-logs/types")
    @Operation(
        summary = "Get distinct audit action type categories",
        description = "Returns all unique action type values present in the audit trail. " +
                      "Use this to populate filter dropdowns in the audit viewer UI."
    )
    @ApiResponse(responseCode = "200", description = "Action types returned")
    public ResponseEntity<List<String>> getAuditActionTypes() {
        log.debug("GET /api/observer/audit-logs/types");
        List<String> types = auditLogService.getDistinctActionTypes();
        return ResponseEntity.ok(types);
    }

    // =========================================================
    // GET /api/observer/audit-logs/fraud — Fraud attempt entries
    // =========================================================

    /**
     * Returns all fraud-related audit entries (paginated).
     * Includes events of type: FRAUD_ATTEMPT, DUPLICATE_VOTE_ATTEMPT, UNAUTHORIZED_ACCESS.
     *
     * GET /api/observer/audit-logs/fraud?page=0&size=20
     *
     * @param page zero-based page number (default: 0)
     * @param size page size (default: 20)
     * @return paginated list of fraud-related audit entries
     */
    @GetMapping("/audit-logs/fraud")
    @Operation(
        summary = "View fraud attempt audit entries",
        description = "Returns paginated audit entries classified as fraud attempts: " +
                      "FRAUD_ATTEMPT, DUPLICATE_VOTE_ATTEMPT, UNAUTHORIZED_ACCESS. " +
                      "Ordered by most recent first."
    )
    @ApiResponse(responseCode = "200", description = "Fraud attempt entries returned")
    public ResponseEntity<Page<AuditLogResponse>> getFraudAttempts(
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size) {

        log.debug("GET /api/observer/audit-logs/fraud — page={}, size={}", page, size);
        int clampedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, clampedSize);

        Page<AuditLogResponse> fraudEntries = auditLogService.getFraudAttempts(pageable);

        return ResponseEntity.ok(fraudEntries);
    }

    // =========================================================
    // GET /api/observer/audit-logs/date-range — Date range filter
    // =========================================================

    /**
     * Returns paginated audit entries within a specific date/time range.
     *
     * GET /api/observer/audit-logs/date-range?from=2025-01-01T00:00:00&to=2025-12-31T23:59:59
     *
     * @param from     start of the date range (ISO-8601 LocalDateTime format)
     * @param to       end of the date range (ISO-8601 LocalDateTime format)
     * @param page     zero-based page number
     * @param size     page size
     * @return paginated audit entries within the specified range
     */
    @GetMapping("/audit-logs/date-range")
    @Operation(
        summary = "Filter audit logs by date range",
        description = "Returns paginated audit entries within the specified date/time range. " +
                      "Both `from` and `to` parameters are required. " +
                      "Format: ISO-8601 LocalDateTime (e.g., `2025-06-01T00:00:00`)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Audit entries within range returned"),
        @ApiResponse(responseCode = "400", description = "Invalid date format or range")
    })
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogsByDateRange(
            @Parameter(description = "Start of date range (ISO-8601)", example = "2025-01-01T00:00:00", required = true)
            @RequestParam LocalDateTime from,

            @Parameter(description = "End of date range (ISO-8601)", example = "2025-12-31T23:59:59", required = true)
            @RequestParam LocalDateTime to,

            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size (max 100)", example = "20")
            @RequestParam(defaultValue = "20") int size) {

        if (to.isBefore(from)) {
            return ResponseEntity.badRequest().build();
        }

        log.debug("GET /api/observer/audit-logs/date-range — from={}, to={}, page={}, size={}",
                from, to, page, size);

        int clampedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, clampedSize, Sort.by("dateAction").descending());

        Page<AuditLogResponse> entries = auditLogService.getLogsByDateRange(from, to, pageable);

        return ResponseEntity.ok(entries);
    }

    // =========================================================
    // GET /api/observer/audit-logs/user/{userId} — User-specific logs
    // =========================================================

    /**
     * Returns all audit entries for a specific user (paginated).
     * Useful for investigating specific user activity or resolving complaints.
     *
     * GET /api/observer/audit-logs/user/{userId}
     *
     * @param userId the ID of the user to query audit entries for
     * @param page   zero-based page number
     * @param size   page size
     * @return paginated audit entries for the specified user
     */
    @GetMapping("/audit-logs/user/{userId}")
    @Operation(
        summary = "Get audit entries for a specific user",
        description = "Returns paginated audit entries associated with a specific user ID. " +
                      "Ordered by most recent first. " +
                      "Note: entries for unauthenticated actions (e.g., failed logins) have utilisateurId = null."
    )
    @ApiResponse(responseCode = "200", description = "User audit entries returned")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogsByUser(
            @Parameter(description = "The ID of the user to query", required = true)
            @PathVariable Long userId,

            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size (max 100)", example = "20")
            @RequestParam(defaultValue = "20") int size) {

        log.debug("GET /api/observer/audit-logs/user/{} — page={}, size={}", userId, page, size);
        int clampedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, clampedSize, Sort.by("dateAction").descending());

        Page<AuditLogResponse> entries = auditLogService.getLogsByUser(userId, pageable);

        return ResponseEntity.ok(entries);
    }

    // =========================================================
    // GET /api/observer/audit-logs/recent — Most recent entries
    // =========================================================

    /**
     * Returns the N most recent audit log entries.
     * Used for real-time monitoring dashboards and live activity feeds.
     *
     * GET /api/observer/audit-logs/recent?limit=10
     *
     * @param limit number of most recent entries to return (default: 10, max: 50)
     * @return list of the most recent audit entries
     */
    @GetMapping("/audit-logs/recent")
    @Operation(
        summary = "Get most recent audit entries",
        description = "Returns the N most recent audit log entries. " +
                      "Use this for live monitoring dashboards and real-time activity feeds."
    )
    @ApiResponse(responseCode = "200", description = "Most recent entries returned")
    public ResponseEntity<Page<AuditLogResponse>> getRecentAuditLogs(
            @Parameter(description = "Number of entries to return (max 50)", example = "10")
            @RequestParam(defaultValue = "10") int limit) {

        int clampedLimit = Math.min(limit, 50);
        log.debug("GET /api/observer/audit-logs/recent?limit={}", clampedLimit);

        Pageable pageable = PageRequest.of(0, clampedLimit, Sort.by("dateAction").descending());
        Page<AuditLogResponse> recent = auditLogService.getRecentLogs(pageable);

        return ResponseEntity.ok(recent);
    }

}
