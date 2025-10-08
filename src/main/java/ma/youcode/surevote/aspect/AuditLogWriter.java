package ma.youcode.surevote.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.domain.entity.LogAudit;
import ma.youcode.surevote.repository.LogAuditRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dedicated component for persisting audit log entries in a NEW transaction.
 *
 * This is required because the AuditAspect runs after service methods that may
 * be annotated with @Transactional(readOnly = true). If the audit save() call
 * participates in that read-only transaction, PostgreSQL rejects the write and
 * marks the transaction as rollback-only, causing an UnexpectedRollbackException.
 *
 * By using Propagation.REQUIRES_NEW, the audit write always runs in its own
 * independent transaction, completely isolated from the calling method's
 * transactional context.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogWriter {

    private final LogAuditRepository logAuditRepository;

    /**
     * Persists an audit log entry in a separate (new) transaction.
     * Never throws — any failure is logged and silently swallowed so that
     * audit logging never disrupts the main business flow.
     *
     * @param entry the audit log entry to persist
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(LogAudit entry) {
        try {
            logAuditRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to persist audit log entry [action={}]: {}",
                    entry.getActionType(), e.getMessage());
        }
    }
}
