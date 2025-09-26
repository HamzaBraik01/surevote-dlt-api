package ma.youcode.surevote.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.domain.entity.LogAudit;
import ma.youcode.surevote.dto.response.AuditLogResponse;
import ma.youcode.surevote.mapper.LogAuditMapper;
import ma.youcode.surevote.repository.LogAuditRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service layer for the immutable audit trail (LogAudit).
 * Encapsulates all access to LogAuditRepository, removing direct repository
 * injection from controllers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AuditLogService {

    private final LogAuditRepository logAuditRepository;
    private final LogAuditMapper logAuditMapper;

    // =========================================================
    // Paginated queries
    // =========================================================

    public Page<AuditLogResponse> getAllLogs(Pageable pageable) {
        return logAuditRepository.findAllByOrderByDateActionDesc(pageable)
                .map(logAuditMapper::toResponse);
    }

    public Page<AuditLogResponse> getLogsByActionType(String actionType, Pageable pageable) {
        return logAuditRepository.findByActionTypeOrderByDateActionDesc(actionType, pageable)
                .map(logAuditMapper::toResponse);
    }

    public Page<AuditLogResponse> searchLogs(String keyword, Pageable pageable) {
        return logAuditRepository.searchLogs(keyword, pageable)
                .map(logAuditMapper::toResponse);
    }

    public Page<AuditLogResponse> getLogsByDateRange(LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return logAuditRepository.findByDateActionBetweenOrderByDateActionDesc(from, to, pageable)
                .map(logAuditMapper::toResponse);
    }

    public Page<AuditLogResponse> getLogsByUser(Long userId, Pageable pageable) {
        return logAuditRepository.findByUtilisateurIdOrderByDateActionDesc(userId, pageable)
                .map(logAuditMapper::toResponse);
    }

    /**
     * Returns the N most recent audit log entries, ordered by dateAction DESC.
     * Uses a dedicated JPQL query rather than findAll to avoid full-table scans.
     *
     * @param pageable use {@code PageRequest.of(0, n)} to get the most recent n records
     * @return page of the most recent audit entries
     */
    public Page<AuditLogResponse> getRecentLogs(Pageable pageable) {
        return logAuditRepository.findMostRecent(pageable)
                .map(logAuditMapper::toResponse);
    }

    // =========================================================
    // Single-record lookup
    // =========================================================

    /**
     * Returns a single audit log entry by its primary key, or empty if not found.
     *
     * @param id the audit log entry's database ID
     * @return an Optional containing the mapped DTO, or empty
     */
    public Optional<AuditLogResponse> getLogById(Long id) {
        return logAuditRepository.findById(id)
                .map(logAuditMapper::toResponse);
    }

    // =========================================================
    // Export (list) queries
    // =========================================================

    /**
     * Exports all audit logs in ascending chronological order.
     * Intended for the Observer journal export endpoint.
     *
     * @return all log records ordered oldest-first
     */
    public List<AuditLogResponse> exportAllLogs() {
        return logAuditRepository.findAllByOrderByDateActionAsc()
                .stream().map(logAuditMapper::toResponse).collect(Collectors.toList());
    }

    /**
     * Exports audit logs filtered by action type in ascending chronological order.
     * Used when the Observer requests a targeted export of a specific event category.
     *
     * @param actionType the action category to filter (e.g., "VOTE_SUBMITTED")
     * @return matching records ordered oldest-first
     */
    public List<AuditLogResponse> exportLogsByActionType(String actionType) {
        return logAuditRepository.findByActionTypeOrderByDateActionAsc(actionType)
                .stream().map(logAuditMapper::toResponse).collect(Collectors.toList());
    }

    /**
     * Exports audit logs within a date range in ascending chronological order.
     *
     * @param from start of the range (inclusive)
     * @param to   end of the range (inclusive)
     * @return matching records ordered oldest-first
     */
    public List<AuditLogResponse> exportLogsByDateRange(LocalDateTime from, LocalDateTime to) {
        return logAuditRepository.findByDateActionBetweenOrderByDateActionAsc(from, to)
                .stream().map(logAuditMapper::toResponse).collect(Collectors.toList());
    }

    // =========================================================
    // Aggregation / metadata queries
    // =========================================================

    /**
     * Returns all distinct action type strings present in the audit trail.
     * Used to populate filter dropdowns in the observer UI.
     *
     * @return sorted list of distinct action type values
     */
    public List<String> getDistinctActionTypes() {
        return logAuditRepository.findDistinctActionTypes();
    }

    /**
     * Counts the number of audit entries created after the given timestamp.
     * Used by the metrics service for dashboard statistics.
     *
     * @param since the reference datetime
     * @return count of log entries after that point in time
     */
    public long countLogsSince(LocalDateTime since) {
        return logAuditRepository.countByDateActionAfter(since);
    }

    /**
     * Returns paginated audit entries classified as fraud attempts.
     * Covers event types: FRAUD_ATTEMPT, DUPLICATE_VOTE_ATTEMPT, UNAUTHORIZED_ACCESS.
     *
     * @param pageable pagination parameters
     * @return paginated fraud-related audit entries ordered most-recent-first
     */
    public Page<AuditLogResponse> getFraudAttempts(Pageable pageable) {
        return logAuditRepository.findFraudAttempts(pageable)
                .map(logAuditMapper::toResponse);
    }

    // =========================================================
    // Private mapping helper
    // =========================================================

    // Mapping handled by MapStruct (LogAuditMapper)
}
