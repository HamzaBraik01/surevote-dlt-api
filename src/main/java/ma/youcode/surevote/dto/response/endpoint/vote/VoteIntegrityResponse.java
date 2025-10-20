package ma.youcode.surevote.dto.response.endpoint.vote;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO dédié à GET /api/vote/integrity/{electionId}.
 * Réponse read-only, sans exposer d'IDs internes de votes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoteIntegrityResponse {
    private Long electionId;
    private int totalVotes;
    private boolean intact;
    private String summary;
    private String verifiedAt;
    private int corruptedCount;
}

