package ma.youcode.surevote.mapper;

import ma.youcode.surevote.domain.entity.CollegeElectoral;
import ma.youcode.surevote.dto.request.CollegeRequest;
import ma.youcode.surevote.dto.response.CollegeResponse;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(config = MapStructConfig.class)
public interface CollegeElectoralMapper {

    @Mapping(target = "nombreElecteurs", ignore = true)
    @Mapping(target = "nombreElections", ignore = true)
    CollegeResponse toResponse(CollegeElectoral college);

    @AfterMapping
    default void fillDerivedCounts(CollegeElectoral source, @MappingTarget CollegeResponse target) {
        int electeurs = source.getElecteurs() != null ? source.getElecteurs().size() : 0;
        int elections = source.getElections() != null ? source.getElections().size() : 0;
        target.setNombreElecteurs(electeurs);
        target.setNombreElections(elections);
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "electeurs", ignore = true)
    @Mapping(target = "elections", ignore = true)
    CollegeElectoral toEntity(CollegeRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "electeurs", ignore = true)
    @Mapping(target = "elections", ignore = true)
    void updateEntity(CollegeRequest request, @MappingTarget CollegeElectoral college);
}

