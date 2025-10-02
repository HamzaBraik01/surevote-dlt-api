package ma.youcode.surevote.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for the Observer metrics dashboard.
 *
 * Provides aggregated, anonymized statistics about the electoral platform
 * without exposing any voter identity or individual ballot information.
 * Accessible by ADMIN and OBSERVATEUR roles only.
 *
 * All metrics are computed server-side and represent read-only snapshots
 * of the current platform state at the time of the request.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricsResponse {

    // =========================================================
    // Platform-level global statistics
    // =========================================================

    /**
     * Timestamp when these metrics were computed.
     * Allows the UI to indicate data freshness.
     */
    private LocalDateTime computedAt;

    /**
     * Total number of registered users across all roles.
     */
    private long totalUtilisateurs;

    /**
     * Number of registered voters (ELECTEUR role).
     */
    private long totalElecteurs;

    /**
     * Number of administrators (ADMIN role).
     */
    private long totalAdministrateurs;

    /**
     * Number of observers (OBSERVATEUR role).
     */
    private long totalObservateurs;

    /**
     * Number of currently active (enabled) user accounts.
     */
    private long totalComptesActifs;

    /**
     * Number of deactivated user accounts.
     */
    private long totalComptesDesactives;

    // =========================================================
    // Election-level statistics
    // =========================================================

    /**
     * Total number of elections ever created on the platform.
     */
    private long totalElections;

    /**
     * Number of elections currently in BROUILLON (draft) state.
     */
    private long electionsBrouillon;

    /**
     * Number of elections in PLANIFIEE (scheduled) state.
     */
    private long electionsPlannifiees;

    /**
     * Number of elections currently OUVERTE (open for voting).
     */
    private long electionsOuvertes;

    /**
     * Number of elections that have been CLOTUREE (closed, results computed).
     */
    private long electionsCloturees;

    /**
     * Number of elections whose results have been PUBLIEE (published).
     */
    private long electionsPubliees;

    /**
     * Number of electoral colleges configured on the platform.
     */
    private long totalColleges;

    // =========================================================
    // Voting statistics
    // =========================================================

    /**
     * Total number of anonymous ballots cast across all elections.
     * This count never references voter identities.
     */
    private long totalVotesCastes;

    /**
     * Total number of emargement (participation) records.
     * Should equal totalVotesCastes in a correctly operating system.
     * Any discrepancy may indicate a data integrity issue.
     */
    private long totalEmargements;

    /**
     * Average participation rate across all closed and published elections.
     * Expressed as a percentage (0.0 to 100.0).
     */
    private double tauxParticipationMoyen;

    /**
     * Number of distinct elections that have received at least one vote.
     */
    private long electionsAvecVotes;

    // =========================================================
    // Per-election participation summary
    // =========================================================

    /**
     * List of participation summaries for each closed or published election.
     * Each entry shows the election name, total votes, and participation rate.
     * Ordered by election end date descending.
     */
    private List<ElectionParticipationSummary> participationParElection;

    // =========================================================
    // Audit trail statistics
    // =========================================================

    /**
     * Total number of entries in the audit log (all time).
     */
    private long totalLogsAudit;

    /**
     * Number of audit log entries recorded in the last 24 hours.
     */
    private long logsAuditDernieres24h;

    /**
     * Number of login failure events recorded in the last 24 hours.
     * Used to assess brute-force activity on the platform.
     */
    private long echecConnexionDernieres24h;

    /**
     * Number of fraud attempt events flagged in the audit trail.
     * Includes duplicate vote attempts, unauthorized access, etc.
     */
    private long totalTentativesFraude;

    /**
     * Distribution of audit log entries by action type.
     * Map of actionType -> count (e.g., "LOGIN_SUCCESS" -> 1240).
     * Useful for security monitoring dashboards.
     */
    private Map<String, Long> distributionActionsAudit;

    // =========================================================
    // Nested summary DTO
    // =========================================================

    /**
     * Summary of participation for a single election.
     * Used in the {@link #participationParElection} list.
     * Contains no voter identity information.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ElectionParticipationSummary {

        /**
         * Internal ID of the election.
         */
        private Long electionId;

        /**
         * Display title of the election.
         */
        private String titre;

        /**
         * Current lifecycle status of the election.
         */
        private String statut;

        /**
         * Number of votes cast in this election.
         */
        private long totalVotes;

        /**
         * Total number of eligible voters for this election.
         * (College size if restricted, or total ELECTEUR count if open.)
         */
        private long totalElecteursEligibles;

        /**
         * Participation rate as a percentage (0.0 to 100.0).
         */
        private double tauxParticipation;

        /**
         * Number of registered candidates in this election.
         */
        private int totalCandidats;

        /**
         * The election's scheduled or actual end date.
         */
        private LocalDateTime dateFin;
    }
}
