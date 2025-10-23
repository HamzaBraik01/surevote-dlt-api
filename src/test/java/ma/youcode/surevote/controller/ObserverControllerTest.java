package ma.youcode.surevote.controller;

import ma.youcode.surevote.dto.response.AuditLogResponse;
import ma.youcode.surevote.dto.response.MetricsResponse;
import ma.youcode.surevote.service.AuditLogService;
import ma.youcode.surevote.service.MetricsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ObserverControllerTest {

    @Mock private MetricsService metricsService;
    @Mock private AuditLogService auditLogService;
    @InjectMocks private ObserverController controller;

    @Test
    void getFullMetrics_shouldReturn200() {
        MetricsResponse metrics = new MetricsResponse();
        when(metricsService.computeFullMetrics()).thenReturn(metrics);

        ResponseEntity<MetricsResponse> response = controller.getFullMetrics();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(metrics);
    }

    @Test
    void getSummaryMetrics_shouldReturn200() {
        MetricsResponse summary = new MetricsResponse();
        when(metricsService.computeSummaryMetrics()).thenReturn(summary);

        ResponseEntity<MetricsResponse> response = controller.getSummaryMetrics();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getAuditLogs_noFilters_shouldReturn200() {
        Page<AuditLogResponse> page = new PageImpl<>(List.of(new AuditLogResponse()));
        when(auditLogService.getAllLogs(any())).thenReturn(page);

        ResponseEntity<Page<AuditLogResponse>> response = controller.getAuditLogs(0, 20, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getAuditLogs_withActionType_shouldReturn200() {
        Page<AuditLogResponse> page = new PageImpl<>(List.of());
        when(auditLogService.getLogsByActionType(eq("LOGIN_SUCCESS"), any())).thenReturn(page);

        ResponseEntity<Page<AuditLogResponse>> response = controller.getAuditLogs(0, 20, "LOGIN_SUCCESS", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getAuditLogs_withKeyword_shouldReturn200() {
        Page<AuditLogResponse> page = new PageImpl<>(List.of());
        when(auditLogService.searchLogs(eq("admin"), any())).thenReturn(page);

        ResponseEntity<Page<AuditLogResponse>> response = controller.getAuditLogs(0, 20, null, "admin");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getAuditLogsByUser_shouldReturn200() {
        Page<AuditLogResponse> page = new PageImpl<>(List.of());
        when(auditLogService.getLogsByUser(eq(1L), any())).thenReturn(page);

        ResponseEntity<Page<AuditLogResponse>> response = controller.getAuditLogsByUser(1L, 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void exportAuditLogs_noFilter_shouldReturn200() {
        when(auditLogService.exportAllLogs()).thenReturn(List.of());

        ResponseEntity<List<AuditLogResponse>> response = controller.exportAuditLogs(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void exportAuditLogs_withActionType_shouldReturn200() {
        when(auditLogService.exportLogsByActionType("VOTE_SUBMITTED")).thenReturn(List.of());

        ResponseEntity<List<AuditLogResponse>> response = controller.exportAuditLogs("VOTE_SUBMITTED");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getAuditActionTypes_shouldReturn200() {
        when(auditLogService.getDistinctActionTypes()).thenReturn(List.of("LOGIN_SUCCESS", "VOTE_SUBMITTED"));

        ResponseEntity<List<String>> response = controller.getAuditActionTypes();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    void getFraudAttempts_shouldReturn200() {
        Page<AuditLogResponse> page = new PageImpl<>(List.of());
        when(auditLogService.getFraudAttempts(any())).thenReturn(page);

        ResponseEntity<Page<AuditLogResponse>> response = controller.getFraudAttempts(0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getRecentAuditLogs_shouldReturn200() {
        Page<AuditLogResponse> page = new PageImpl<>(List.of());
        when(auditLogService.getRecentLogs(any())).thenReturn(page);

        ResponseEntity<Page<AuditLogResponse>> response = controller.getRecentAuditLogs(10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
