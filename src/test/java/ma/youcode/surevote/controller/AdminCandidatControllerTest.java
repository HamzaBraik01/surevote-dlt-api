package ma.youcode.surevote.controller;

import ma.youcode.surevote.dto.request.CandidatRequest;
import ma.youcode.surevote.dto.response.CandidatResponse;
import ma.youcode.surevote.dto.response.endpoint.admin.CandidateCountResponse;
import ma.youcode.surevote.mapper.endpoint.AdminEndpointMapper;
import ma.youcode.surevote.service.CandidatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminCandidatControllerTest {

    @Mock private CandidatService candidatService;
    @Mock private AdminEndpointMapper adminEndpointMapper;
    @InjectMocks private AdminCandidatController controller;

    @Test
    void addCandidat_shouldReturn201() {
        CandidatRequest req = new CandidatRequest();
        req.setNom("Doe");
        req.setPrenom("John");
        CandidatResponse resp = new CandidatResponse();
        when(candidatService.addCandidat(1L, req)).thenReturn(resp);

        ResponseEntity<CandidatResponse> response = controller.addCandidat(1L, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void getCandidatsByElection_shouldReturn200() {
        when(candidatService.findAllByElection(1L)).thenReturn(List.of(new CandidatResponse()));
        ResponseEntity<List<CandidatResponse>> response = controller.getCandidatsByElection(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void getCandidatById_shouldReturn200() {
        when(candidatService.findById(1L)).thenReturn(new CandidatResponse());
        ResponseEntity<CandidatResponse> response = controller.getCandidatById(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void updateCandidat_shouldReturn200() {
        CandidatRequest req = new CandidatRequest();
        when(candidatService.updateCandidat(1L, req)).thenReturn(new CandidatResponse());
        ResponseEntity<CandidatResponse> response = controller.updateCandidat(1L, req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void deleteCandidat_shouldReturn204() {
        ResponseEntity<Void> response = controller.deleteCandidat(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(candidatService).deleteCandidat(1L);
    }

    @Test
    void updatePhotoUrl_withValidUrl_shouldReturn200() {
        Map<String, String> body = Map.of("photoUrl", " http://example.com/photo.jpg ");
        when(candidatService.updatePhotoUrl(1L, "http://example.com/photo.jpg")).thenReturn(new CandidatResponse());

        ResponseEntity<CandidatResponse> response = controller.updatePhotoUrl(1L, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void updatePhotoUrl_withNullUrl_shouldReturn400() {
        Map<String, String> body = Map.of("other", "value");

        ResponseEntity<CandidatResponse> response = controller.updatePhotoUrl(1L, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updatePhotoUrl_withBlankUrl_shouldReturn400() {
        Map<String, String> body = Map.of("photoUrl", "  ");

        ResponseEntity<CandidatResponse> response = controller.updatePhotoUrl(1L, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateProgrammePdfUrl_withValidUrl_shouldReturn200() {
        Map<String, String> body = Map.of("programmePdfUrl", " http://example.com/prog.pdf ");
        when(candidatService.updateProgrammePdfUrl(1L, "http://example.com/prog.pdf")).thenReturn(new CandidatResponse());

        ResponseEntity<CandidatResponse> response = controller.updateProgrammePdfUrl(1L, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void updateProgrammePdfUrl_withNullUrl_shouldReturn400() {
        Map<String, String> body = Map.of("other", "value");

        ResponseEntity<CandidatResponse> response = controller.updateProgrammePdfUrl(1L, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateProgrammePdfUrl_withBlankUrl_shouldReturn400() {
        Map<String, String> body = Map.of("programmePdfUrl", "  ");

        ResponseEntity<CandidatResponse> response = controller.updateProgrammePdfUrl(1L, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void countCandidats_withEnoughCandidates() {
        when(candidatService.countByElection(1L)).thenReturn(3L);
        CandidateCountResponse countResp = new CandidateCountResponse();
        when(adminEndpointMapper.toCandidateCountResponse(eq(1L), eq(3L), eq(true), anyString())).thenReturn(countResp);

        ResponseEntity<CandidateCountResponse> response = controller.countCandidats(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void countCandidats_withNotEnoughCandidates() {
        when(candidatService.countByElection(1L)).thenReturn(1L);
        CandidateCountResponse countResp = new CandidateCountResponse();
        when(adminEndpointMapper.toCandidateCountResponse(eq(1L), eq(1L), eq(false), anyString())).thenReturn(countResp);

        ResponseEntity<CandidateCountResponse> response = controller.countCandidats(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
