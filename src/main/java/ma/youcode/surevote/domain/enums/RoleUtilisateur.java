package ma.youcode.surevote.domain.enums;

/**
 * Enumeration of user roles in the SUREVOTE platform.
 * Controls access to endpoints and UI views via Spring Security RBAC.
 */
public enum RoleUtilisateur {

    /** Platform administrator — manages elections, candidates, colleges, and audit logs. */
    ADMIN,

    /** Registered voter — can browse elections, submit ballots, and verify receipts. */
    ELECTEUR,

    /** Read-only observer — can consult metrics and export audit journals. */
    OBSERVATEUR
}
