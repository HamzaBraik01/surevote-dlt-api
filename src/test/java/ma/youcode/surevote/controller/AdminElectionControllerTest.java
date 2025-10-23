package ma.youcode.surevote.controller;

import ma.youcode.surevote.domain.enums.StatutElection;
import ma.youcode.surevote.dto.request.ElectionRequest;
import ma.youcode.surevote.dto.response.ElectionResponse;
import ma.youcode.surevote.dto.response.ResultatResponse;
import ma.youcode.surevote.dto.response.endpoint.admin.ElectionIntegrityReportResponse;
import ma.youcode.surevote.dto.response.endpoint.admin.ElectionStatsResponse;
import ma.youcode.surevote.mapper.endpoint.AdminEndpointMapper;
import ma.youcode.surevote.service.ElectionService;
import ma.youcode.surevote.service.ResultatService;
import ma.youcode.surevote.service.VoteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminElectionControllerTest {

    @Mock private ElectionService electionService;
    @Mock private ResultatService resultatService;
    @Mock private VoteService voteService;
    @Mock private AdminEndpointMapper adminEndpointMapper;
    @InjectMocks private AdminElectionController controller;

    @Test
    void createElection_shouldReturn201() {
        ElectionRequest req = new ElectionRequest();
        req.setTitre("Test");
        ElectionResponse resp = new ElectionResponse();
        when(electionService.createElection(req)).thenReturn(resp);

        ResponseEntity<ElectionResponse> response = controller.createElection(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void getAllElections_shouldReturn200() {
        when(electionService.findAll()).thenReturn(List.of(new ElectionResponse()));

        ResponseEntity<List<ElectionResponse>> response = controller.getAllElections();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void getAllElectionsPaged_shouldReturn200() {
        Page<ElectionResponse> page = new PageImpl<>(List.of(new ElectionResponse()));
        when(electionService.findAll(any())).thenReturn(page);

        ResponseEntity<Page<ElectionResponse>> response = controller.getAllElectionsPaged(0, 20, "dateDebut", "desc");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getAllElectionsPaged_sortAsc() {
        Page<ElectionResponse> page = new PageImpl<>(List.of());
        when(electionService.findAll(any())).thenReturn(page);

        ResponseEntity<Page<ElectionResponse>> response = controller.getAllElectionsPaged(0, 200, "titre", "asc");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getElectionById_shouldReturn200() {
        when(electionService.findById(1L)).thenReturn(new ElectionResponse());

        ResponseEntity<ElectionResponse> response = controller.getElectionById(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getElectionsByStatus_shouldReturn200() {
        when(electionService.findByStatut(StatutElection.OUVERTE)).thenReturn(List.of());

        ResponseEntity<List<ElectionResponse>> response = controller.getElectionsByStatus(StatutElection.OUVERTE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void searchElections_shouldReturn200() {
        when(electionService.search("test")).thenReturn(List.of());

        ResponseEntity<List<ElectionResponse>> response = controller.searchElections("test");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void updateElection_shouldReturn200() {
        ElectionRequest req = new ElectionRequest();
        when(electionService.updateElection(1L, req)).thenReturn(new ElectionResponse());

        ResponseEntity<ElectionResponse> response = controller.updateElection(1L, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void deleteElection_shouldReturn204() {
        ResponseEntity<Void> response = controller.deleteElection(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(electionService).deleteElection(1L);
    }

    @Test
    void planElection_shouldReturn200() {
        when(electionService.planifierElection(1L)).thenReturn(new ElectionResponse());
        ResponseEntity<ElectionResponse> response = controller.planElection(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void openElection_shouldReturn200() {
        when(electionService.ouvrirScrutin(1L)).thenReturn(new ElectionResponse());
        ResponseEntity<ElectionResponse> response = controller.openElection(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void closeElection_shouldReturn200() {
        when(electionService.cloturerScrutin(1L)).thenReturn(new ElectionResponse());
        ResponseEntity<ElectionResponse> response = controller.closeElection(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void publishResults_shouldReturn200() {
        when(electionService.publierResultats(1L)).thenReturn(new ElectionResponse());
        ResponseEntity<ElectionResponse> response = controller.publishResults(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getElectionResults_shouldReturn200() {
        when(resultatService.getResultsForAdmin(1L)).thenReturn(new ResultatResponse());
        ResponseEntity<ResultatResponse> response = controller.getElectionResults(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getElectionStats_shouldReturn200() {
        when(voteService.countVotesByElection(1L)).thenReturn(10L);
        when(voteService.countParticipantsByElection(1L)).thenReturn(10L);
        ElectionStatsResponse statsResp = new ElectionStatsResponse();
        when(adminEndpointMapper.toElectionStatsResponse(1L, 10L, 10L, false)).thenReturn(statsResp);

        ResponseEntity<ElectionStatsResponse> response = controller.getElectionStats(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getElectionStats_withMismatch() {
        when(voteService.countVotesByElection(1L)).thenReturn(10L);
        when(voteService.countParticipantsByElection(1L)).thenReturn(9L);
        ElectionStatsResponse statsResp = new ElectionStatsResponse();
        when(adminEndpointMapper.toElectionStatsResponse(1L, 10L, 9L, true)).thenReturn(statsResp);

        ResponseEntity<ElectionStatsResponse> response = controller.getElectionStats(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void verifyIntegrity_noTampered_shouldReturn200() {
        when(resultatService.computeVoteTableChecksum(1L)).thenReturn("abc123");
        when(voteService.countVotesByElection(1L)).thenReturn(5L);
        when(voteService.countParticipantsByElection(1L)).thenReturn(5L);
        when(resultatService.detectTamperedVotes(1L)).thenReturn(Collections.emptyList());
        ElectionIntegrityReportResponse reportResp = new ElectionIntegrityReportResponse();
        when(adminEndpointMapper.toElectionIntegrityReportResponse(eq(1L), eq("abc123"), eq(5L), eq(5L), eq(false), eq(Collections.emptyList()), eq(false), anyString())).thenReturn(reportResp);

        ResponseEntity<ElectionIntegrityReportResponse> response = controller.verifyIntegrity(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void verifyIntegrity_withTampered_shouldReturn200() {
        when(resultatService.computeVoteTableChecksum(1L)).thenReturn("abc123");
        when(voteService.countVotesByElection(1L)).thenReturn(5L);
        when(voteService.countParticipantsByElection(1L)).thenReturn(5L);
        List<Long> tampered = List.of(99L);
        when(resultatService.detectTamperedVotes(1L)).thenReturn(tampered);
        ElectionIntegrityReportResponse reportResp = new ElectionIntegrityReportResponse();
        when(adminEndpointMapper.toElectionIntegrityReportResponse(eq(1L), eq("abc123"), eq(5L), eq(5L), eq(false), eq(tampered), eq(true), anyString())).thenReturn(reportResp);

        ResponseEntity<ElectionIntegrityReportResponse> response = controller.verifyIntegrity(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
