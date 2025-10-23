package ma.youcode.surevote.service;

import ma.youcode.surevote.domain.entity.LogAudit;
import ma.youcode.surevote.dto.response.AuditLogResponse;
import ma.youcode.surevote.mapper.LogAuditMapper;
import ma.youcode.surevote.repository.LogAuditRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogService")
class AuditLogServiceTest {

    @Mock private LogAuditRepository repo;
    @Mock private LogAuditMapper mapper;
    @InjectMocks private AuditLogService service;

    private LogAudit log1;
    private AuditLogResponse resp1;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        log1 = LogAudit.of("LOGIN_SUCCESS", "test", "127.0.0.1", 1L, "user@test.com");
        resp1 = AuditLogResponse.builder().id(1L).build();
        pageable = PageRequest.of(0, 10);
    }

    // ── Paginated queries ───────────────────────────────────────

    @Test @DisplayName("getAllLogs delegates to repo and maps")
    void getAllLogs() {
        Page<LogAudit> page = new PageImpl<>(List.of(log1));
        when(repo.findAllByOrderByDateActionDesc(pageable)).thenReturn(page);
        when(mapper.toResponse(log1)).thenReturn(resp1);

        Page<AuditLogResponse> result = service.getAllLogs(pageable);
        assertThat(result.getContent()).containsExactly(resp1);
    }

    @Test @DisplayName("getLogsByActionType filters by action type")
    void getLogsByActionType() {
        Page<LogAudit> page = new PageImpl<>(List.of(log1));
        when(repo.findByActionTypeOrderByDateActionDesc("LOGIN", pageable)).thenReturn(page);
        when(mapper.toResponse(log1)).thenReturn(resp1);

        Page<AuditLogResponse> result = service.getLogsByActionType("LOGIN", pageable);
        assertThat(result.getContent()).containsExactly(resp1);
    }

    @Test @DisplayName("searchLogs delegates keyword search")
    void searchLogs() {
        Page<LogAudit> page = new PageImpl<>(List.of(log1));
        when(repo.searchLogs("test", pageable)).thenReturn(page);
        when(mapper.toResponse(log1)).thenReturn(resp1);

        Page<AuditLogResponse> result = service.searchLogs("test", pageable);
        assertThat(result.getContent()).containsExactly(resp1);
    }

    @Test @DisplayName("getLogsByDateRange filters by date range")
    void getLogsByDateRange() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();
        Page<LogAudit> page = new PageImpl<>(List.of(log1));
        when(repo.findByDateActionBetweenOrderByDateActionDesc(from, to, pageable)).thenReturn(page);
        when(mapper.toResponse(log1)).thenReturn(resp1);

        Page<AuditLogResponse> result = service.getLogsByDateRange(from, to, pageable);
        assertThat(result.getContent()).containsExactly(resp1);
    }

    @Test @DisplayName("getLogsByUser filters by user ID")
    void getLogsByUser() {
        Page<LogAudit> page = new PageImpl<>(List.of(log1));
        when(repo.findByUtilisateurIdOrderByDateActionDesc(42L, pageable)).thenReturn(page);
        when(mapper.toResponse(log1)).thenReturn(resp1);

        Page<AuditLogResponse> result = service.getLogsByUser(42L, pageable);
        assertThat(result.getContent()).containsExactly(resp1);
    }

    @Test @DisplayName("getRecentLogs returns most recent entries")
    void getRecentLogs() {
        Page<LogAudit> page = new PageImpl<>(List.of(log1));
        when(repo.findMostRecent(pageable)).thenReturn(page);
        when(mapper.toResponse(log1)).thenReturn(resp1);

        Page<AuditLogResponse> result = service.getRecentLogs(pageable);
        assertThat(result.getContent()).containsExactly(resp1);
    }

    // ── Single-record lookup ────────────────────────────────────

    @Test @DisplayName("getLogById returns present Optional when found")
    void getLogById_found() {
        when(repo.findById(1L)).thenReturn(Optional.of(log1));
        when(mapper.toResponse(log1)).thenReturn(resp1);

        Optional<AuditLogResponse> result = service.getLogById(1L);
        assertThat(result).isPresent().contains(resp1);
    }

    @Test @DisplayName("getLogById returns empty Optional when not found")
    void getLogById_notFound() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertThat(service.getLogById(99L)).isEmpty();
    }

    // ── Export queries ──────────────────────────────────────────

    @Test @DisplayName("exportAllLogs returns all logs in asc order")
    void exportAllLogs() {
        when(repo.findAllByOrderByDateActionAsc()).thenReturn(List.of(log1));
        when(mapper.toResponse(log1)).thenReturn(resp1);

        List<AuditLogResponse> result = service.exportAllLogs();
        assertThat(result).containsExactly(resp1);
    }

    @Test @DisplayName("exportLogsByActionType filters and exports")
    void exportLogsByActionType() {
        when(repo.findByActionTypeOrderByDateActionAsc("VOTE")).thenReturn(List.of(log1));
        when(mapper.toResponse(log1)).thenReturn(resp1);

        List<AuditLogResponse> result = service.exportLogsByActionType("VOTE");
        assertThat(result).containsExactly(resp1);
    }

    @Test @DisplayName("exportLogsByDateRange filters and exports")
    void exportLogsByDateRange() {
        LocalDateTime from = LocalDateTime.now().minusDays(7);
        LocalDateTime to = LocalDateTime.now();
        when(repo.findByDateActionBetweenOrderByDateActionAsc(from, to)).thenReturn(List.of(log1));
        when(mapper.toResponse(log1)).thenReturn(resp1);

        List<AuditLogResponse> result = service.exportLogsByDateRange(from, to);
        assertThat(result).containsExactly(resp1);
    }

    // ── Aggregation / metadata ──────────────────────────────────

    @Test @DisplayName("getDistinctActionTypes delegates to repo")
    void getDistinctActionTypes() {
        when(repo.findDistinctActionTypes()).thenReturn(List.of("LOGIN", "VOTE"));
        assertThat(service.getDistinctActionTypes()).containsExactly("LOGIN", "VOTE");
    }

    @Test @DisplayName("countLogsSince delegates to repo")
    void countLogsSince() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        when(repo.countByDateActionAfter(since)).thenReturn(42L);
        assertThat(service.countLogsSince(since)).isEqualTo(42L);
    }

    @Test @DisplayName("getFraudAttempts returns fraud page")
    void getFraudAttempts() {
        Page<LogAudit> page = new PageImpl<>(List.of(log1));
        when(repo.findFraudAttempts(pageable)).thenReturn(page);
        when(mapper.toResponse(log1)).thenReturn(resp1);

        Page<AuditLogResponse> result = service.getFraudAttempts(pageable);
        assertThat(result.getContent()).containsExactly(resp1);
    }
}
