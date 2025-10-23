package ma.youcode.surevote.service;

import ma.youcode.surevote.domain.entity.Candidat;
import ma.youcode.surevote.domain.entity.CollegeElectoral;
import ma.youcode.surevote.domain.entity.Election;
import ma.youcode.surevote.domain.entity.LogAudit;
import ma.youcode.surevote.domain.entity.Utilisateur;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import ma.youcode.surevote.domain.enums.StatutElection;
import ma.youcode.surevote.dto.response.MetricsResponse;
import ma.youcode.surevote.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetricsService")
class MetricsServiceTest {

    @Mock private UtilisateurRepository utilisateurRepo;
    @Mock private ElectionRepository electionRepo;
    @Mock private VoteRepository voteRepo;
    @Mock private EmargementRepository emargementRepo;
    @Mock private CollegeElectoralRepository collegeRepo;
    @Mock private LogAuditRepository logAuditRepo;
    @InjectMocks private MetricsService service;

    // ── computeFullMetrics ─────────────────────────────────────

    @Test @DisplayName("computeFullMetrics aggregates all metrics")
    void computeFullMetrics_success() {
        // User stats
        when(utilisateurRepo.count()).thenReturn(100L);
        when(utilisateurRepo.countByRole(RoleUtilisateur.ELECTEUR)).thenReturn(70L);
        when(utilisateurRepo.countByRole(RoleUtilisateur.ADMIN)).thenReturn(5L);
        when(utilisateurRepo.countByRole(RoleUtilisateur.OBSERVATEUR)).thenReturn(3L);
        List<Utilisateur> activeUsers = new ArrayList<>(Collections.nCopies(90, (Utilisateur) null));
        List<Utilisateur> disabledUsers = new ArrayList<>(Collections.nCopies(10, (Utilisateur) null));
        when(utilisateurRepo.findAllByIsEnabledTrue()).thenReturn(activeUsers);
        when(utilisateurRepo.findAllByIsEnabledFalse()).thenReturn(disabledUsers);

        // Election stats
        when(electionRepo.count()).thenReturn(20L);
        when(electionRepo.countByStatut(any(StatutElection.class))).thenReturn(4L);
        when(collegeRepo.count()).thenReturn(5L);

        // Vote stats
        when(voteRepo.countAllVotes()).thenReturn(500L);
        when(emargementRepo.count()).thenReturn(500L);
        List<Object[]> emptyParticipation = Collections.emptyList();
        when(emargementRepo.countParticipantsGroupedByClosedElection()).thenReturn(emptyParticipation);
        when(voteRepo.countDistinctElectionsWithVotes()).thenReturn(10L);

        // Participation summaries — no closed elections
        when(electionRepo.findAll()).thenReturn(Collections.emptyList());

        // Audit stats
        when(logAuditRepo.count()).thenReturn(1000L);
        when(logAuditRepo.countByDateActionAfter(any())).thenReturn(50L);
        when(logAuditRepo.countLoginFailuresByIp(eq(""), any())).thenReturn(3L);
        List<LogAudit> emptyLogs = Collections.emptyList();
        when(logAuditRepo.findFraudAttempts(any())).thenReturn(new PageImpl<>(emptyLogs));
        List<Object[]> emptyActionTypes = Collections.emptyList();
        when(logAuditRepo.countByActionType()).thenReturn(emptyActionTypes);

        MetricsResponse result = service.computeFullMetrics();

        assertThat(result.getTotalUtilisateurs()).isEqualTo(100L);
        assertThat(result.getTotalElecteurs()).isEqualTo(70L);
        assertThat(result.getTotalVotesCastes()).isEqualTo(500L);
        assertThat(result.getComputedAt()).isNotNull();
    }

    // ── computeSummaryMetrics ───────────────────────────────────

    @Test @DisplayName("computeSummaryMetrics returns lightweight metrics")
    void computeSummaryMetrics() {
        when(utilisateurRepo.count()).thenReturn(50L);
        when(utilisateurRepo.countByRole(RoleUtilisateur.ELECTEUR)).thenReturn(40L);
        when(electionRepo.count()).thenReturn(10L);
        when(electionRepo.countByStatut(StatutElection.OUVERTE)).thenReturn(2L);
        when(electionRepo.countByStatut(StatutElection.PUBLIEE)).thenReturn(3L);
        when(voteRepo.countAllVotes()).thenReturn(200L);
        List<Object[]> emptyP = Collections.emptyList();
        when(emargementRepo.countParticipantsGroupedByClosedElection()).thenReturn(emptyP);

        MetricsResponse result = service.computeSummaryMetrics();

        assertThat(result.getTotalUtilisateurs()).isEqualTo(50L);
        assertThat(result.getTotalElections()).isEqualTo(10L);
    }

    // ── computeAverageParticipationRate ─────────────────────────

    @Test @DisplayName("computeAverageParticipationRate returns 0 when no rows")
    void avgParticipation_noRows() {
        List<Object[]> empty = Collections.emptyList();
        when(emargementRepo.countParticipantsGroupedByClosedElection()).thenReturn(empty);
        assertThat(service.computeAverageParticipationRate()).isEqualTo(0.0);
    }

    @Test @DisplayName("computeAverageParticipationRate computes correctly with college")
    void avgParticipation_withCollege() {
        CollegeElectoral college = CollegeElectoral.builder().id(1L).build();
        Election election = Election.builder().id(1L).statut(StatutElection.CLOTUREE)
                .collegeElectoral(college).build();

        Object[] row = new Object[]{1L, 50L}; // electionId=1, participants=50
        List<Object[]> rows = new ArrayList<>();
        rows.add(row);
        when(emargementRepo.countParticipantsGroupedByClosedElection()).thenReturn(rows);
        when(electionRepo.findById(1L)).thenReturn(Optional.of(election));
        when(collegeRepo.countMembersByCollegeId(1L)).thenReturn(100L);

        double result = service.computeAverageParticipationRate();
        assertThat(result).isEqualTo(50.0);
    }

    @Test @DisplayName("computeAverageParticipationRate computes correctly without college")
    void avgParticipation_noCollege() {
        Election election = Election.builder().id(1L).statut(StatutElection.CLOTUREE).build();

        Object[] row = new Object[]{1L, 25L};
        List<Object[]> rows = new ArrayList<>();
        rows.add(row);
        when(emargementRepo.countParticipantsGroupedByClosedElection()).thenReturn(rows);
        when(electionRepo.findById(1L)).thenReturn(Optional.of(election));
        when(utilisateurRepo.countByRole(RoleUtilisateur.ELECTEUR)).thenReturn(100L);

        double result = service.computeAverageParticipationRate();
        assertThat(result).isEqualTo(25.0);
    }

    @Test @DisplayName("computeAverageParticipationRate skips null election")
    void avgParticipation_nullElection() {
        Object[] row = new Object[]{999L, 10L};
        List<Object[]> rows = new ArrayList<>();
        rows.add(row);
        when(emargementRepo.countParticipantsGroupedByClosedElection()).thenReturn(rows);
        when(electionRepo.findById(999L)).thenReturn(Optional.empty());

        assertThat(service.computeAverageParticipationRate()).isEqualTo(0.0);
    }

    @Test @DisplayName("computeAverageParticipationRate handles zero eligibles")
    void avgParticipation_zeroEligibles() {
        Election election = Election.builder().id(1L).statut(StatutElection.CLOTUREE).build();

        Object[] row = new Object[]{1L, 10L};
        List<Object[]> rows = new ArrayList<>();
        rows.add(row);
        when(emargementRepo.countParticipantsGroupedByClosedElection()).thenReturn(rows);
        when(electionRepo.findById(1L)).thenReturn(Optional.of(election));
        when(utilisateurRepo.countByRole(RoleUtilisateur.ELECTEUR)).thenReturn(0L);

        assertThat(service.computeAverageParticipationRate()).isEqualTo(0.0);
    }

    // ── computeLoginFailures24h ────────────────────────────────

    @Test @DisplayName("computeLoginFailures24h returns count")
    void loginFailures_success() {
        when(logAuditRepo.countLoginFailuresByIp("", LocalDateTime.MIN)).thenReturn(3L);
        assertThat(service.computeLoginFailures24h(LocalDateTime.MIN)).isEqualTo(3L);
    }

    @Test @DisplayName("computeLoginFailures24h returns 0 on exception")
    void loginFailures_exception() {
        when(logAuditRepo.countLoginFailuresByIp(eq(""), any())).thenThrow(new RuntimeException("DB"));
        assertThat(service.computeLoginFailures24h(LocalDateTime.now())).isEqualTo(0L);
    }

    // ── computeFraudAttempts ───────────────────────────────────

    @Test @DisplayName("computeFraudAttempts returns total elements")
    void fraudAttempts_success() {
        List<LogAudit> logList = new ArrayList<>(Collections.nCopies(5, LogAudit.system("FRAUD", "test", "1.2.3.4")));
        Page<LogAudit> page = new PageImpl<>(logList);
        when(logAuditRepo.findFraudAttempts(any())).thenReturn(page);
        assertThat(service.computeFraudAttempts()).isEqualTo(5L);
    }

    @Test @DisplayName("computeFraudAttempts returns 0 on exception")
    void fraudAttempts_exception() {
        when(logAuditRepo.findFraudAttempts(any())).thenThrow(new RuntimeException("err"));
        assertThat(service.computeFraudAttempts()).isEqualTo(0L);
    }

    // ── buildParticipationSummaries ────────────────────────────

    @Test @DisplayName("buildParticipationSummaries returns list for closed elections")
    void participationSummaries_closed() {
        Candidat c1 = Candidat.builder().id(1L).nom("A").prenom("B").build();
        Election election = Election.builder()
                .id(1L).titre("E1").statut(StatutElection.CLOTUREE)
                .dateDebut(LocalDateTime.now().minusDays(2))
                .dateFin(LocalDateTime.now().minusDays(1))
                .candidats(List.of(c1))
                .build();

        when(electionRepo.findAll()).thenReturn(List.of(election));
        when(utilisateurRepo.countByRole(RoleUtilisateur.ELECTEUR)).thenReturn(100L);
        when(emargementRepo.countByElection_Id(1L)).thenReturn(50L);
        when(voteRepo.countByElectionId(1L)).thenReturn(50L);

        List<MetricsResponse.ElectionParticipationSummary> result = service.buildParticipationSummaries();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitre()).isEqualTo("E1");
        assertThat(result.get(0).getTauxParticipation()).isEqualTo(50.0);
    }

    @Test @DisplayName("buildParticipationSummaries with college uses college size")
    void participationSummaries_withCollege() {
        CollegeElectoral college = CollegeElectoral.builder().id(1L).build();
        Election election = Election.builder()
                .id(1L).titre("E1").statut(StatutElection.PUBLIEE)
                .dateDebut(LocalDateTime.now().minusDays(2))
                .dateFin(LocalDateTime.now().minusDays(1))
                .collegeElectoral(college)
                .candidats(Collections.emptyList())
                .build();

        when(electionRepo.findAll()).thenReturn(List.of(election));
        when(utilisateurRepo.countByRole(RoleUtilisateur.ELECTEUR)).thenReturn(200L);
        when(collegeRepo.countMembersByCollegeId(1L)).thenReturn(50L);
        when(emargementRepo.countByElection_Id(1L)).thenReturn(25L);
        when(voteRepo.countByElectionId(1L)).thenReturn(25L);

        List<MetricsResponse.ElectionParticipationSummary> result = service.buildParticipationSummaries();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTauxParticipation()).isEqualTo(50.0);
    }

    @Test @DisplayName("buildParticipationSummaries handles null candidats")
    void participationSummaries_nullCandidats() {
        Election election = Election.builder()
                .id(1L).titre("E1").statut(StatutElection.CLOTUREE)
                .dateDebut(LocalDateTime.now().minusDays(2))
                .dateFin(LocalDateTime.now().minusDays(1))
                .build();
        election.setCandidats(null);

        when(electionRepo.findAll()).thenReturn(List.of(election));
        when(utilisateurRepo.countByRole(RoleUtilisateur.ELECTEUR)).thenReturn(100L);
        when(emargementRepo.countByElection_Id(1L)).thenReturn(0L);
        when(voteRepo.countByElectionId(1L)).thenReturn(0L);

        List<MetricsResponse.ElectionParticipationSummary> result = service.buildParticipationSummaries();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTotalCandidats()).isZero();
    }

    @Test @DisplayName("buildParticipationSummaries handles zero eligibles")
    void participationSummaries_zeroEligibles() {
        Election election = Election.builder()
                .id(1L).titre("E1").statut(StatutElection.CLOTUREE)
                .dateDebut(LocalDateTime.now().minusDays(2))
                .dateFin(LocalDateTime.now().minusDays(1))
                .candidats(Collections.emptyList()).build();

        when(electionRepo.findAll()).thenReturn(List.of(election));
        when(utilisateurRepo.countByRole(RoleUtilisateur.ELECTEUR)).thenReturn(0L);
        when(emargementRepo.countByElection_Id(1L)).thenReturn(0L);
        when(voteRepo.countByElectionId(1L)).thenReturn(0L);

        List<MetricsResponse.ElectionParticipationSummary> result = service.buildParticipationSummaries();
        assertThat(result.get(0).getTauxParticipation()).isEqualTo(0.0);
    }

    @Test @DisplayName("buildParticipationSummaries skips BROUILLON/OUVERTE")
    void participationSummaries_skipsNonClosed() {
        Election brouillon = Election.builder()
                .id(1L).titre("B").statut(StatutElection.BROUILLON)
                .dateDebut(LocalDateTime.now()).dateFin(LocalDateTime.now().plusDays(1))
                .build();
        Election ouverte = Election.builder()
                .id(2L).titre("O").statut(StatutElection.OUVERTE)
                .dateDebut(LocalDateTime.now()).dateFin(LocalDateTime.now().plusDays(1))
                .build();

        when(electionRepo.findAll()).thenReturn(List.of(brouillon, ouverte));
        when(utilisateurRepo.countByRole(RoleUtilisateur.ELECTEUR)).thenReturn(100L);

        List<MetricsResponse.ElectionParticipationSummary> result = service.buildParticipationSummaries();
        assertThat(result).isEmpty();
    }

    // ── buildAuditActionDistribution ───────────────────────────

    @Test @DisplayName("buildAuditActionDistribution builds map from raw data")
    void auditDistribution_success() {
        List<Object[]> rows = List.of(
                new Object[]{"LOGIN", 100L},
                new Object[]{"VOTE", 50L}
        );
        when(logAuditRepo.countByActionType()).thenReturn(rows);

        Map<String, Long> result = service.buildAuditActionDistribution();
        assertThat(result).containsEntry("LOGIN", 100L).containsEntry("VOTE", 50L);
    }

    @Test @DisplayName("buildAuditActionDistribution skips null action types")
    void auditDistribution_skipsNull() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{null, 10L});
        when(logAuditRepo.countByActionType()).thenReturn(rows);

        Map<String, Long> result = service.buildAuditActionDistribution();
        assertThat(result).isEmpty();
    }

    @Test @DisplayName("buildAuditActionDistribution returns empty on exception")
    void auditDistribution_exception() {
        when(logAuditRepo.countByActionType()).thenThrow(new RuntimeException("err"));

        Map<String, Long> result = service.buildAuditActionDistribution();
        assertThat(result).isEmpty();
    }
}
