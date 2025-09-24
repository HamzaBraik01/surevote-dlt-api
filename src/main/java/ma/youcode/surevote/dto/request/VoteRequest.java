package ma.youcode.surevote.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for submitting a ballot in an election.
 * Used by POST /api/vote/submit
 *
 * SECURITY NOTE: This DTO intentionally contains ONLY the election and candidate IDs.
 * The voter's identity is extracted from the JWT token server-side — it is never
 * accepted from the request body to prevent identity spoofing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Payload de soumission d'un vote anonyme")
public class VoteRequest {

    /**
     * The ID of the election the voter is casting their ballot in.
     * Must reference an existing, currently OUVERTE election.
     */
    @Schema(description = "ID de l'élection ouverte", example = "1")
    @NotNull(message = "L'identifiant de l'élection est obligatoire")
    private Long electionId;

    /**
     * The ID of the candidate the voter is voting for.
     * Must reference a candidate registered in the specified election.
     * Cross-election candidate injection is prevented at the service layer.
     */
    @Schema(description = "ID du candidat choisi", example = "3")
    @NotNull(message = "L'identifiant du candidat est obligatoire")
    private Long candidatId;
}
