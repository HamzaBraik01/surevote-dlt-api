package ma.youcode.surevote.mapper.endpoint;

import ma.youcode.surevote.dto.response.EligibilityResponse;
import ma.youcode.surevote.dto.response.endpoint.vote.VoteIntegrityResponse;
import ma.youcode.surevote.dto.response.endpoint.vote.VotedElectionsResponse;
import ma.youcode.surevote.mapper.MapStructConfig;
import ma.youcode.surevote.service.VoteService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface VoteEndpointMapper {

    @Mapping(source = "electionId", target = "electionId")
    @Mapping(source = "result.eligible", target = "eligible")
    @Mapping(source = "result.alreadyVoted", target = "alreadyVoted")
    @Mapping(source = "result.requiresOtp", target = "requiresOtp")
    @Mapping(source = "result.message", target = "message")
    @Mapping(source = "result.existingReceipt", target = "existingReceipt")
    EligibilityResponse toEligibilityResponse(Long electionId, VoteService.EligibilityResult result);

    default VotedElectionsResponse toVotedElectionsResponse(List<Long> votedElectionIds) {
        int total = votedElectionIds != null ? votedElectionIds.size() : 0;
        return VotedElectionsResponse.builder()
                .votedElectionIds(votedElectionIds)
                .totalVoted(total)
                .build();
    }

    @Mapping(source = "report.electionId", target = "electionId")
    @Mapping(source = "report.totalVotes", target = "totalVotes")
    @Mapping(source = "report.intact", target = "intact")
    @Mapping(target = "summary", expression = "java(report.getSummary())")
    @Mapping(target = "verifiedAt", expression = "java(report.verifiedAt().toString())")
    @Mapping(source = "report.corruptedVotes", target = "corruptedCount")
    VoteIntegrityResponse toVoteIntegrityResponse(VoteService.IntegrityReport report);
}

