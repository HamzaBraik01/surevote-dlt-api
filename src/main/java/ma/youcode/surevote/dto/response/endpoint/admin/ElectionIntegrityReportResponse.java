package ma.youcode.surevote.dto.response.endpoint.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectionIntegrityReportResponse {
    private Long electionId;
    private String tableChecksum;
    private long totalVotes;
    private long totalParticipants;
    private boolean countMismatch;
    private List<Long> tamperedVoteIds;
    private boolean integrityViolated;
    private String verifiedAt;
}

