package ma.youcode.surevote.domain.enums;

/**
 * Represents the full lifecycle state machine of an Election.
 * Transitions: BROUILLON → PLANIFIEE → OUVERTE → CLOTUREE → PUBLIEE
 * Automated via @Scheduled tasks; manual override allowed for ADMIN.
 */
public enum StatutElection {

    /**
     * Draft state — election is being configured, not yet visible to voters.
     */
    BROUILLON,

    /**
     * Planned state — election is fully configured and scheduled, awaiting start date.
     */
    PLANIFIEE,

    /**
     * Open state — ballot is active and eligible voters can submit votes.
     */
    OUVERTE,

    /**
     * Closed state — voting period has ended; results are being computed.
     */
    CLOTUREE,

    /**
     * Published state — results are officially published and publicly accessible.
     */
    PUBLIEE
}
