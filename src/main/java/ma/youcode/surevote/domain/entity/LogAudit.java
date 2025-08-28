package ma.youcode.surevote.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Immutable audit trail entity for the SUREVOTE platform.
 *
 * Every sensitive action on the platform is silently intercepted via Spring AOP
 * and persisted as a LogAudit record. This table is strictly append-only —
 * no UPDATE or DELETE operations are ever issued against it.
 *
 * Accessible by ADMIN and OBSERVATEUR roles without exposing vote content.
 */
@Entity
@Table(
    name = "log_audit",
    indexes = {
        @Index(name = "idx_log_audit_date_action",  columnList = "date_action"),
        @Index(name = "idx_log_audit_action_type",  columnList = "action_type"),
        @Index(name = "idx_log_audit_utilisateur_id", columnList = "utilisateur_id")
    }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * UTC timestamp of when the audited action occurred.
     * Immutable after creation — never modified.
     */
    @Column(name = "date_action", nullable = false, updatable = false)
    private LocalDateTime dateAction;

    /**
     * Categorized type of action that was intercepted.
     * Examples: LOGIN_SUCCESS, LOGIN_FAILURE, VOTE_SUBMITTED, ELECTION_CREATED,
     *           ELECTION_OPENED, ELECTION_CLOSED, USER_DEACTIVATED, ROLE_UPDATED,
     *           FRAUD_ATTEMPT, OTP_VERIFIED, REPORT_EXPORTED
     */
    @Column(name = "action_type", nullable = false, length = 60, updatable = false)
    private String actionType;

    /**
     * Human-readable details about the action.
     * Contains contextual information like entity IDs, user emails, or error reasons.
     * Never contains sensitive data such as passwords or vote choices.
     */
    @Column(name = "details", columnDefinition = "TEXT", updatable = false)
    private String details;

    /**
     * IP address of the client that originated the action.
     * Extracted from the HTTP request via X-Forwarded-For or RemoteAddr.
     */
    @Column(name = "adresse_ip", length = 45, updatable = false)
    private String adresseIp;

    /**
     * Optional reference to the user who performed the action.
     * Stored as a plain Long (not a FK relationship) to preserve log integrity
     * even if the user account is later deactivated or deleted.
     * Null for unauthenticated actions (e.g., failed login attempts).
     */
    @Column(name = "utilisateur_id", updatable = false)
    private Long utilisateurId;

    /**
     * Optional email snapshot of the acting user at the time of the action.
     * Stored in plain text for readability in audit reports, even if the account
     * is later modified.
     */
    @Column(name = "utilisateur_email", length = 150, updatable = false)
    private String utilisateurEmail;

    // -----------------------------------------------------------------------
    // Factory method — enforces immutability by controlling construction
    // -----------------------------------------------------------------------

    /**
     * Static factory method to create a fully-populated, immutable LogAudit record.
     *
     * @param actionType      the type/category of action (e.g., "LOGIN_SUCCESS")
     * @param details         human-readable description of what happened
     * @param adresseIp       originating IP address of the request
     * @param utilisateurId   ID of the acting user (null if unauthenticated)
     * @param utilisateurEmail email of the acting user (null if unauthenticated)
     * @return a ready-to-persist LogAudit instance
     */
    public static LogAudit of(String actionType,
                               String details,
                               String adresseIp,
                               Long utilisateurId,
                               String utilisateurEmail) {
        return LogAudit.builder()
                .dateAction(LocalDateTime.now())
                .actionType(actionType)
                .details(details)
                .adresseIp(adresseIp)
                .utilisateurId(utilisateurId)
                .utilisateurEmail(utilisateurEmail)
                .build();
    }

    /**
     * Convenience factory for system-level or anonymous actions.
     */
    public static LogAudit system(String actionType, String details, String adresseIp) {
        return of(actionType, details, adresseIp, null, "SYSTEM");
    }

    // -----------------------------------------------------------------------
    // Prevent mutation after construction — no setters exposed
    // -----------------------------------------------------------------------

    @PreUpdate
    protected void onUpdate() {
        throw new UnsupportedOperationException(
            "LogAudit records are immutable. No UPDATE operations are permitted on the audit trail."
        );
    }

    @Override
    public String toString() {
        return "LogAudit{" +
                "id=" + id +
                ", dateAction=" + dateAction +
                ", actionType='" + actionType + '\'' +
                ", utilisateurEmail='" + utilisateurEmail + '\'' +
                ", adresseIp='" + adresseIp + '\'' +
                '}';
    }
}
