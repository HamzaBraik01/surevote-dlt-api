package ma.youcode.surevote.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.youcode.surevote.domain.enums.StatutElection;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO representing the full results of a closed or published election.
 *
 * This object is returned by:
 *   - GET /api/elections/{id}/results       (public, only when PUBLIEE)
 *   - GET /api/admin/elections/{id}/results (admin, accessible from CLOTUREE)
 *
 * Contains a ranked list of candidates with their vote counts and percentages,
 * plus participation statistics for the election report.
 *
 * SECURITY NOTE: This DTO contains aggregated, anonymised data only.
 * No voter identity or individual ballot information is ever included.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResultatResponse {

    // =========================================================
    // Election context
    // =========================================================

    /** Internal ID of the election. */
    private Long electionId;

    /** Title of the election. */
    private String titrElection;

    /** Description of the election. */
    private String descriptionElection;

    /** Scheduled start date/time of the election. */
    private LocalDateTime dateDebut;

    /** Scheduled end date/time of the election. */
    private LocalDateTime dateFin;

    /** Current lifecycle status of the election. */
    private StatutElection statut;

    // =========================================================
    // Participation statistics
    // =========================================================

    /**
     * Total number of anonymous ballots cast in this election.
     * Equals the number of emargement records for consistency verification.
     */
    private long totalVotes;

    /**
     * Total number of registered voters who were eligible to vote in this election.
     * Either the college size (if restricted) or the total ELECTEUR count (if open).
     */
    private long totalElecteursEligibles;

    /**
     * Participation rate as a percentage (0.00 to 100.00).
     * Computed as: (totalVotes / totalElecteursEligibles) * 100
     * Rounded to 2 decimal places.
     */
    private double tauxParticipation;

    /**
     * Abstention rate as a percentage (0.00 to 100.00).
     * Computed as: 100.00 - tauxParticipation
     */
    private double tauxAbstention;

    /**
     * Number of eligible voters who did NOT participate.
     * Computed as: totalElecteursEligibles - totalVotes
     */
    private long abstentions;

    // =========================================================
    // Ranked candidate results
    // =========================================================

    /**
     * Ranked list of candidates with their vote counts and percentages.
     * Ordered by vote count descending (winner first).
     * In case of a tie, candidates are ordered alphabetically by last name.
     */
    private List<CandidatResultat> resultats;

    /**
     * The winning candidate (the first entry in the resultats list).
     * Null if there are no votes, or if the election is still in progress.
     */
    private CandidatResultat gagnant;

    /**
     * Whether the result is a tie (two or more candidates share the highest vote count).
     */
    private boolean egalite;

    // =========================================================
    // Report metadata
    // =========================================================

    /** Timestamp when the results were computed (immediately after CLOTUREE transition). */
    private LocalDateTime dateCalculResultats;

    /** Timestamp when the results were officially published (PUBLIEE transition). */
    private LocalDateTime datePublication;

    /** Checksum hash of the Vote table at time of result computation. Used for FR-12 integrity. */
    private String checksumIntegrite;

    // =========================================================
    // Inner DTO — per-candidate result row
    // =========================================================

    /**
     * Represents a single candidate's result within the election.
     * Contains the candidate's identity, vote count, percentage, and ranking.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CandidatResultat {

        /** Internal ID of the candidate. */
        private Long candidatId;

        /** Full name: "Prénom NOM" */
        private String nomComplet;

        /** Last name of the candidate. */
        private String nom;

        /** First name of the candidate. */
        private String prenom;

        /** Political party or organisational affiliation. */
        private String affiliationOuParti;

        /** URL to the candidate's profile photo (for display in result UI). */
        private String photoUrl;

        /**
         * Number of anonymous ballots received by this candidate.
         * Computed via SQL COUNT aggregation on the Vote table.
         */
        private long nombreVotes;

        /**
         * Percentage of total votes received by this candidate.
         * Computed as: (nombreVotes / totalVotes) * 100
         * Rounded to 2 decimal places.
         * Returns 0.00 if totalVotes is 0 (no division by zero).
         */
        private double pourcentage;

        /**
         * Final ranking position of this candidate (1 = winner).
         * Ties result in shared rank positions.
         */
        private int rang;

        /**
         * Whether this candidate is the declared winner.
         * True only for the candidate(s) with the highest vote count.
         */
        private boolean estGagnant;

        // -----------------------------------------------------------------------
        // Convenience factory method
        // -----------------------------------------------------------------------

        /**
         * Creates a CandidatResultat from raw aggregation data.
         *
         * @param candidatId       the candidate's ID
         * @param nom              last name
         * @param prenom           first name
         * @param affiliation      party/organisation
         * @param photoUrl         photo URL
         * @param nombreVotes      vote count for this candidate
         * @param totalVotes       total votes in the election (for percentage computation)
         * @param rang             final ranking position
         * @return fully populated CandidatResultat
         */
        public static CandidatResultat of(
                Long candidatId,
                String nom,
                String prenom,
                String affiliation,
                String photoUrl,
                long nombreVotes,
                long totalVotes,
                int rang) {

            double pourcentage = (totalVotes > 0)
                    ? Math.round(((double) nombreVotes / totalVotes) * 10000.0) / 100.0
                    : 0.00;

            String safeNom = (nom != null) ? nom : "";
            String safePrenom = (prenom != null) ? prenom : "";

            return CandidatResultat.builder()
                    .candidatId(candidatId)
                    .nom(safeNom)
                    .prenom(safePrenom)
                    .nomComplet(safePrenom + " " + safeNom.toUpperCase())
                    .affiliationOuParti(affiliation)
                    .photoUrl(photoUrl)
                    .nombreVotes(nombreVotes)
                    .pourcentage(pourcentage)
                    .rang(rang)
                    .estGagnant(rang == 1)
                    .build();
        }
    }

    // =========================================================
    // Convenience factory
    // =========================================================

    /**
     * Computes the taux d'abstention from participation data and sets the gagnant field.
     * Call this after constructing the object with the builder to finalise computed fields.
     */
    public void finalise() {
        // Compute abstention
        this.abstentions = Math.max(0L, totalElecteursEligibles - totalVotes);
        this.tauxAbstention = (totalElecteursEligibles > 0)
                ? Math.round((100.0 - tauxParticipation) * 100.0) / 100.0
                : 0.00;

        // Identify winner(s)
        if (resultats != null && !resultats.isEmpty()) {
            this.gagnant = resultats.get(0); // Sorted desc by vote count
            long maxVotes = gagnant.getNombreVotes();
            long winnersCount = resultats.stream()
                    .filter(r -> r.getNombreVotes() == maxVotes)
                    .count();
            this.egalite = winnersCount > 1;
        }
    }
}
