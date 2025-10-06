package ma.youcode.surevote.mapper;

import ma.youcode.surevote.domain.entity.Candidat;
import ma.youcode.surevote.dto.request.CandidatRequest;
import ma.youcode.surevote.dto.response.CandidatResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(config = MapStructConfig.class)
public interface CandidatMapper {

    @Mapping(source = "election.id", target = "electionId")
    @Mapping(target = "nombreVotes", ignore = true)
    @Mapping(target = "pourcentageVotes", ignore = true)
    @Mapping(target = "rang", ignore = true)
    CandidatResponse toResponse(Candidat candidat);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "election", ignore = true)
    @Mapping(target = "votes", ignore = true)
    Candidat toEntity(CandidatRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "election", ignore = true)
    @Mapping(target = "votes", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(CandidatRequest request, @MappingTarget Candidat candidat);
}

