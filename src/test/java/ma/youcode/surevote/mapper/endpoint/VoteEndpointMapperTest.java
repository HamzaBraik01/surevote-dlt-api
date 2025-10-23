package ma.youcode.surevote.mapper.endpoint;

import ma.youcode.surevote.dto.response.endpoint.vote.VotedElectionsResponse;
import ma.youcode.surevote.service.VoteService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VoteEndpointMapperTest {

    /**
     * Tests the 'default' method toVotedElectionsResponse which has custom logic.
     * We can test this directly since it's a default method on the interface.
     */

    // Create a minimal test implementation
    private final VoteEndpointMapper mapper = new VoteEndpointMapper() {
        @Override
        public ma.youcode.surevote.dto.response.EligibilityResponse toEligibilityResponse(Long electionId, VoteService.EligibilityResult result) {
            return null;
        }

        @Override
        public ma.youcode.surevote.dto.response.endpoint.vote.VoteIntegrityResponse toVoteIntegrityResponse(VoteService.IntegrityReport report) {
            return null;
        }
    };

    @Test
    void toVotedElectionsResponse_withIds_shouldSetTotalCorrectly() {
        List<Long> ids = List.of(1L, 2L, 3L);
        VotedElectionsResponse response = mapper.toVotedElectionsResponse(ids);
        assertThat(response.getVotedElectionIds()).hasSize(3);
        assertThat(response.getTotalVoted()).isEqualTo(3);
    }

    @Test
    void toVotedElectionsResponse_withNull_shouldSetTotalToZero() {
        VotedElectionsResponse response = mapper.toVotedElectionsResponse(null);
        assertThat(response.getTotalVoted()).isEqualTo(0);
    }

    @Test
    void toVotedElectionsResponse_withEmptyList_shouldSetTotalToZero() {
        VotedElectionsResponse response = mapper.toVotedElectionsResponse(List.of());
        assertThat(response.getVotedElectionIds()).isEmpty();
        assertThat(response.getTotalVoted()).isEqualTo(0);
    }
}
