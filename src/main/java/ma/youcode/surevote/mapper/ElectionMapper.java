package ma.youcode.surevote.mapper;

import ma.youcode.surevote.domain.entity.Election;
import ma.youcode.surevote.dto.request.ElectionRequest;
import ma.youcode.surevote.dto.response.ElectionResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        config = MapStructConfig.class,
        uses = {CollegeElectoralMapper.class, CandidatMapper.class}
)
public interface ElectionMapper {

    /**
     * Default list-view mapping: does not include candidates (avoids lazy collection access).
     * Computed fields remain service responsibility.
     */
    @Mapping(target = "candidats", ignore = true)
    @Mapping(target = "totalVotes", ignore = true)
    @Mapping(target = "totalParticipants", ignore = true)
    @Mapping(target = "totalCandidats", ignore = true)
    @Mapping(target = "tauxParticipation", ignore = true)
    @Mapping(target = "totalElecteursEligibles", ignore = true)
    ElectionResponse toResponse(Election election);

    /**
     * Details-view mapping: includes candidates list if already loaded.
     * Computed fields remain service responsibility.
     */
    @Mapping(target = "totalVotes", ignore = true)
    @Mapping(target = "totalParticipants", ignore = true)
    @Mapping(target = "totalCandidats", ignore = true)
    @Mapping(target = "tauxParticipation", ignore = true)
    @Mapping(target = "totalElecteursEligibles", ignore = true)
    ElectionResponse toResponseWithCandidats(Election election);

    /**
     * Request -> Entity (create). Does NOT set statut, timestamps, relations (college/candidats/votes/emargements).
     * Those remain in service/business logic.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "statut", ignore = true)
    @Mapping(target = "dateCreation", ignore = true)
    @Mapping(target = "dateModification", ignore = true)
    @Mapping(target = "collegeElectoral", ignore = true)
    @Mapping(target = "candidats", ignore = true)
    @Mapping(target = "votes", ignore = true)
    @Mapping(target = "emargements", ignore = true)
    Election toEntity(ElectionRequest request);

    /**
     * Request -> Entity (update). Same constraints as create.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "statut", ignore = true)
    @Mapping(target = "dateCreation", ignore = true)
    @Mapping(target = "dateModification", ignore = true)
    @Mapping(target = "collegeElectoral", ignore = true)
    @Mapping(target = "candidats", ignore = true)
    @Mapping(target = "votes", ignore = true)
    @Mapping(target = "emargements", ignore = true)
    void updateEntity(ElectionRequest request, @MappingTarget Election election);
}

