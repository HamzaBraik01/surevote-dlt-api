package ma.youcode.surevote.dto.response.endpoint.vote;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VotedElectionsResponse {
    private List<Long> votedElectionIds;
    private int totalVoted;
}

