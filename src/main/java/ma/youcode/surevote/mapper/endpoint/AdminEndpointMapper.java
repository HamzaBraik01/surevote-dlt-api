package ma.youcode.surevote.mapper.endpoint;

import ma.youcode.surevote.dto.response.endpoint.admin.*;
import ma.youcode.surevote.mapper.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface AdminEndpointMapper {

    ElectionStatsResponse toElectionStatsResponse(Long electionId,
                                                 long totalVotes,
                                                 long totalParticipants,
                                                 boolean integrityMismatch);

    @Mapping(target = "verifiedAt", source = "verifiedAt")
    ElectionIntegrityReportResponse toElectionIntegrityReportResponse(Long electionId,
                                                                     String tableChecksum,
                                                                     long totalVotes,
                                                                     long totalParticipants,
                                                                     boolean countMismatch,
                                                                     List<Long> tamperedVoteIds,
                                                                     boolean integrityViolated,
                                                                     String verifiedAt);

    CollegeMemberCountResponse toCollegeMemberCountResponse(Long collegeId, long memberCount);

    CollegeMembershipResponse toCollegeMembershipResponse(Long collegeId, Long electeurId, boolean isMember);

    CandidateCountResponse toCandidateCountResponse(Long electionId,
                                                    long count,
                                                    boolean readyToOpen,
                                                    String message);
}

