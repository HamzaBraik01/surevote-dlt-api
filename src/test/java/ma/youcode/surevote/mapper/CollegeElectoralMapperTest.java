package ma.youcode.surevote.mapper;

import ma.youcode.surevote.domain.entity.CollegeElectoral;
import ma.youcode.surevote.domain.entity.Electeur;
import ma.youcode.surevote.domain.entity.Election;
import ma.youcode.surevote.dto.response.CollegeResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CollegeElectoralMapperTest {

    /**
     * Tests the default @AfterMapping method fillDerivedCounts
     */
    private final CollegeElectoralMapper mapper = new CollegeElectoralMapper() {
        @Override
        public CollegeResponse toResponse(CollegeElectoral college) {
            // minimal impl; the test will call fillDerivedCounts directly
            CollegeResponse r = new CollegeResponse();
            r.setId(college.getId());
            r.setNom(college.getNom());
            return r;
        }

        @Override
        public CollegeElectoral toEntity(ma.youcode.surevote.dto.request.CollegeRequest request) {
            return null;
        }

        @Override
        public void updateEntity(ma.youcode.surevote.dto.request.CollegeRequest request, CollegeElectoral college) {}
    };

    @Test
    void fillDerivedCounts_withPopulatedLists() {
        CollegeElectoral college = CollegeElectoral.builder().id(1L).nom("Test").build();
        college.setElecteurs(List.of(new Electeur(), new Electeur()));
        college.setElections(List.of(Election.builder().build()));

        CollegeResponse target = new CollegeResponse();
        mapper.fillDerivedCounts(college, target);

        assertThat(target.getNombreElecteurs()).isEqualTo(2);
        assertThat(target.getNombreElections()).isEqualTo(1);
    }

    @Test
    void fillDerivedCounts_withNullLists() {
        CollegeElectoral college = CollegeElectoral.builder().id(1L).nom("Test").build();
        college.setElecteurs(null);
        college.setElections(null);

        CollegeResponse target = new CollegeResponse();
        mapper.fillDerivedCounts(college, target);

        assertThat(target.getNombreElecteurs()).isEqualTo(0);
        assertThat(target.getNombreElections()).isEqualTo(0);
    }
}
