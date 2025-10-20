package ma.youcode.surevote.dto.response.endpoint.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectionStatsResponse {
    private Long electionId;
    private long totalVotes;
    private long totalParticipants;
    private boolean integrityMismatch;
}

