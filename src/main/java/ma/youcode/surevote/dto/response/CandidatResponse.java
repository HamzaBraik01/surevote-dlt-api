package ma.youcode.surevote.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for Candidat data exposed via the REST API.
 * Used in both the ballot display (voter view) and result pages.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidatResponse {

    private Long id;

    private String nom;

    private String prenom;

    /**
     * Computed full name: "Prenom NOM"
     */
    private String nomComplet;

    private String affiliationOuParti;

    private String biographie;

    private String photoUrl;

    private String programmePdfUrl;

    private Long electionId;

    /**
     * Vote count — only populated in result views (CLOTUREE / PUBLIEE elections).
     * Null in ballot display views to prevent premature result leakage.
     */
    private Long nombreVotes;

    /**
     * Percentage of total votes received by this candidate.
     * Computed as (nombreVotes / totalVotesElection) * 100.
     * Only populated in result views.
     */
    private Double pourcentageVotes;

    /**
     * Ranking position in the election results (1 = winner).
     * Only populated in result views.
     */
    private Integer rang;
}
