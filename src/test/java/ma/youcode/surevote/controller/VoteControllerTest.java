package ma.youcode.surevote.controller;

import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import ma.youcode.surevote.config.RateLimitingConfig;
import ma.youcode.surevote.dto.request.VoteRequest;
import ma.youcode.surevote.dto.response.EligibilityResponse;
import ma.youcode.surevote.dto.response.VoteReceiptResponse;
import ma.youcode.surevote.dto.response.endpoint.vote.VoteIntegrityResponse;
import ma.youcode.surevote.dto.response.endpoint.vote.VotedElectionsResponse;
import ma.youcode.surevote.mapper.endpoint.VoteEndpointMapper;
import ma.youcode.surevote.service.VoteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoteControllerTest {

    @Mock private VoteService voteService;
    @Mock private VoteEndpointMapper voteEndpointMapper;
    @Mock private RateLimitingConfig rateLimiting;
    @Mock private HttpServletRequest httpRequest;
    @InjectMocks private VoteController voteController;

    @Test
    void checkEligibility_shouldReturn200() {
        VoteService.EligibilityResult result = new VoteService.EligibilityResult(true, false, false, "Eligible", null);
        EligibilityResponse eligResp = new EligibilityResponse();
        when(voteService.checkEligibility(1L)).thenReturn(result);
        when(voteEndpointMapper.toEligibilityResponse(1L, result)).thenReturn(eligResp);

        ResponseEntity<EligibilityResponse> response = voteController.checkEligibility(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(eligResp);
    }

    @Test
    void submitVote_shouldReturn200() {
        VoteRequest request = new VoteRequest();
        request.setElectionId(1L);
        request.setCandidatId(2L);
        VoteReceiptResponse receipt = new VoteReceiptResponse();
        receipt.setRecuCryptographique("uuid-123");
        when(voteService.submitVote(request)).thenReturn(receipt);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        Bucket bucket = mock(Bucket.class);
        when(bucket.tryConsume(1)).thenReturn(true);
        when(rateLimiting.voteSubmitBucket(anyString())).thenReturn(bucket);

        ResponseEntity<VoteReceiptResponse> response = voteController.submitVote(request, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(receipt);
    }

    @Test
    void submitVote_shouldReturn429WhenRateLimited() {
        VoteRequest request = new VoteRequest();
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        Bucket bucket = mock(Bucket.class);
        when(bucket.tryConsume(1)).thenReturn(false);
        when(rateLimiting.voteSubmitBucket(anyString())).thenReturn(bucket);

        ResponseEntity<VoteReceiptResponse> response = voteController.submitVote(request, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void getMyVotedElections_shouldReturn200() {
        List<Long> ids = List.of(1L, 2L);
        VotedElectionsResponse vResp = VotedElectionsResponse.builder().votedElectionIds(ids).totalVoted(2).build();
        when(voteService.getVotedElectionIds()).thenReturn(ids);
        when(voteEndpointMapper.toVotedElectionsResponse(ids)).thenReturn(vResp);

        ResponseEntity<VotedElectionsResponse> response = voteController.getMyVotedElections();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getTotalVoted()).isEqualTo(2);
    }

    @Test
    void getMyReceipt_shouldReturn200() {
        VoteReceiptResponse receipt = new VoteReceiptResponse();
        when(voteService.getMyReceipt(1L)).thenReturn(receipt);

        ResponseEntity<VoteReceiptResponse> response = voteController.getMyReceipt(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void checkVoteIntegrity_shouldReturn200() {
        VoteService.IntegrityReport report = new VoteService.IntegrityReport(1L, 10, 0, true, LocalDateTime.now());
        VoteIntegrityResponse intResp = new VoteIntegrityResponse();
        when(voteService.verifyIntegrity(1L)).thenReturn(report);
        when(voteEndpointMapper.toVoteIntegrityResponse(report)).thenReturn(intResp);

        ResponseEntity<VoteIntegrityResponse> response = voteController.checkVoteIntegrity(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
