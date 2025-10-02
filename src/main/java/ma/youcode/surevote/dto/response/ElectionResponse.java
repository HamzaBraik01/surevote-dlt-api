package ma.youcode.surevote.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.youcode.surevote.domain.enums.StatutElection;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for Election data exposed via the REST API.
 *
 * Contains all publicly safe fields of an Election entity,
 * including computed metrics and candidate summaries.
 * Never exposes internal database IDs in a way that leaks architecture.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectionResponse {

    private Long id;

    private String titre;

    private String description;

    private LocalDateTime dateDebut;

    private LocalDateTime dateFin;

    /**
     * Current lifecycle status of the election.
     * Enum: BROUILLON, PLANIFIEE, OUVERTE, CLOTUREE, PUBLIEE
     */
    private StatutElection statut;

    private LocalDateTime dateCreation;

    private LocalDateTime dateModification;

    /**
     * Optional electoral college restriction.
     * If null, the election is open to all registered voters.
     */
    private CollegeResponse collegeElectoral;

    /**
     * List of candidates registered for this election.
     * Only included when fetching a single election's details
     * (not in list views, for performance).
     */
    private List<CandidatResponse> candidats;

    // -----------------------------------------------------------------------
    // Computed / Aggregated fields
    // -----------------------------------------------------------------------

    /**
     * Total number of ballots cast in this election.
     * Available only after the election has been closed (CLOTUREE/PUBLIEE).
     */
    private Long totalVotes;

    /**
     * Total number of voters who participated (emargement count).
     * Equals totalVotes in a correctly operating system.
     */
    private Long totalParticipants;

    /**
     * Total number of candidates registered for this election.
     */
    private Integer totalCandidats;

    /**
     * Participation rate as a percentage (0.0 to 100.0).
     * Computed as (totalParticipants / totalEligibleVoters) * 100.
     * Available only for CLOTUREE and PUBLIEE elections.
     */
    private Double tauxParticipation;

    /**
     * Total number of eligible voters for this election.
     * Either the college size (if restricted) or the total ELECTEUR count.
     */
    private Long totalElecteursEligibles;

    // -----------------------------------------------------------------------
    // Status helpers for UI rendering
    // -----------------------------------------------------------------------

    /**
     * Whether the ballot is currently open for voting.
     */
    public boolean isOuverte() {
        return StatutElection.OUVERTE.equals(statut);
    }

    /**
     * Whether results are publicly available.
     */
    public boolean isPubliee() {
        return StatutElection.PUBLIEE.equals(statut);
    }

    /**
     * Whether the election has been closed (awaiting publication).
     */
    public boolean isCloturee() {
        return StatutElection.CLOTUREE.equals(statut);
    }

    /**
     * Whether the election is in draft or planning state (hidden from voters).
     */
    public boolean isInPreparation() {
        return StatutElection.BROUILLON.equals(statut)
                || StatutElection.PLANIFIEE.equals(statut);
    }
}
