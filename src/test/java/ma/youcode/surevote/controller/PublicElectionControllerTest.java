package ma.youcode.surevote.controller;

import ma.youcode.surevote.dto.response.CandidatResponse;
import ma.youcode.surevote.dto.response.ElectionResponse;
import ma.youcode.surevote.dto.response.ResultatResponse;
import ma.youcode.surevote.dto.response.VoteReceiptResponse;
import ma.youcode.surevote.dto.response.endpoint.publicapi.ReceiptExistsResponse;
import ma.youcode.surevote.mapper.endpoint.PublicEndpointMapper;
import ma.youcode.surevote.service.CandidatService;
import ma.youcode.surevote.service.ElectionService;
import ma.youcode.surevote.service.ResultatService;
import ma.youcode.surevote.service.VoteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicElectionControllerTest {

    @Mock private ElectionService electionService;
    @Mock private CandidatService candidatService;
    @Mock private ResultatService resultatService;
    @Mock private VoteService voteService;
    @Mock private PublicEndpointMapper publicEndpointMapper;
    @InjectMocks private PublicElectionController controller;

    @Test
    void getAllVisibleElections_noKeyword_shouldReturn200() {
        when(electionService.findAllVisible()).thenReturn(List.of(new ElectionResponse()));
        ResponseEntity<List<ElectionResponse>> response = controller.getAllVisibleElections("");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void getAllVisibleElections_withKeyword_shouldReturn200() {
        when(electionService.search("test")).thenReturn(List.of());
        ResponseEntity<List<ElectionResponse>> response = controller.getAllVisibleElections("test");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getOpenElections_shouldReturn200() {
        when(electionService.findAllOpen()).thenReturn(List.of());
        ResponseEntity<List<ElectionResponse>> response = controller.getOpenElections();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getPublishedElections_shouldReturn200() {
        when(electionService.findAllPublished()).thenReturn(List.of());
        ResponseEntity<List<ElectionResponse>> response = controller.getPublishedElections();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getElectionById_shouldReturn200() {
        when(electionService.findById(1L)).thenReturn(new ElectionResponse());
        ResponseEntity<ElectionResponse> response = controller.getElectionById(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getCandidatesByElection_shouldReturn200() {
        when(candidatService.findAllByElection(1L)).thenReturn(List.of());
        ResponseEntity<List<CandidatResponse>> response = controller.getCandidatesByElection(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getCandidateById_shouldReturn200() {
        when(candidatService.findById(2L)).thenReturn(new CandidatResponse());
        ResponseEntity<CandidatResponse> response = controller.getCandidateById(1L, 2L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getPublicResults_shouldReturn200() {
        when(resultatService.getPublicResults(1L)).thenReturn(new ResultatResponse());
        ResponseEntity<ResultatResponse> response = controller.getPublicResults(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void verifyReceipt_shouldReturn200() {
        VoteReceiptResponse receipt = new VoteReceiptResponse();
        when(voteService.verifyReceipt("uuid-123")).thenReturn(receipt);

        ResponseEntity<VoteReceiptResponse> response = controller.verifyReceipt("uuid-123");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(receipt);
    }

    @Test
    void receiptExists_shouldReturn200() {
        when(resultatService.receiptExists("uuid-123")).thenReturn(true);
        ReceiptExistsResponse resp = new ReceiptExistsResponse();
        when(publicEndpointMapper.toReceiptExistsResponse(eq(true), eq("uuid-123"), anyString())).thenReturn(resp);

        ResponseEntity<ReceiptExistsResponse> response = controller.receiptExists("uuid-123");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void receiptExists_notFound_shouldReturn200() {
        when(resultatService.receiptExists("uuid-404")).thenReturn(false);
        ReceiptExistsResponse resp = new ReceiptExistsResponse();
        when(publicEndpointMapper.toReceiptExistsResponse(eq(false), eq("uuid-404"), anyString())).thenReturn(resp);

        ResponseEntity<ReceiptExistsResponse> response = controller.receiptExists("uuid-404");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
