package ma.youcode.surevote.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for LogAudit entries exposed via the REST API.
 *
 * Accessible by ADMIN and OBSERVATEUR roles.
 * Never exposes vote content or voter ballot choices.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogResponse {

    private Long id;

    /**
     * UTC timestamp of when the audited action occurred.
     */
    private LocalDateTime dateAction;

    /**
     * Categorized type of the audited action.
     * Examples: LOGIN_SUCCESS, LOGIN_FAILURE, VOTE_SUBMITTED,
     *           ELECTION_CREATED, ELECTION_OPENED, ELECTION_CLOSED,
     *           USER_DEACTIVATED, ROLE_UPDATED, FRAUD_ATTEMPT, OTP_VERIFIED
     */
    private String actionType;

    /**
     * Human-readable details about the action.
     * Contains contextual information — never sensitive data.
     */
    private String details;

    /**
     * IP address of the client that originated the action.
     */
    private String adresseIp;

    /**
     * ID of the user who performed the action.
     * Null for unauthenticated actions (e.g., failed login attempts).
     */
    private Long utilisateurId;

    /**
     * Email snapshot of the acting user at the time of the action.
     * Preserved even if the account is later modified or deleted.
     */
    private String utilisateurEmail;
}
