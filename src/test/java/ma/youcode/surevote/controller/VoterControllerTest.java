package ma.youcode.surevote.controller;

import ma.youcode.surevote.domain.entity.Electeur;
import ma.youcode.surevote.domain.entity.Utilisateur;
import ma.youcode.surevote.dto.response.ElectionResponse;
import ma.youcode.surevote.service.ElectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoterControllerTest {

    @Mock private ElectionService electionService;
    @InjectMocks private VoterController controller;

    @Test
    void getEligibleElections_shouldReturn200() {
        Electeur voter = new Electeur();
        voter.setId(1L);
        when(electionService.findEligibleForVoter(1L)).thenReturn(List.of(new ElectionResponse()));
        ResponseEntity<List<ElectionResponse>> response = controller.getEligibleElections(voter);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void getEligibleElections_empty_shouldReturn200() {
        Electeur voter = new Electeur();
        voter.setId(2L);
        when(electionService.findEligibleForVoter(2L)).thenReturn(List.of());
        ResponseEntity<List<ElectionResponse>> response = controller.getEligibleElections(voter);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }
}
