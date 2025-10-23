package ma.youcode.surevote.service;

import ma.youcode.surevote.domain.entity.*;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import ma.youcode.surevote.domain.enums.StatutElection;
import ma.youcode.surevote.dto.request.VoteRequest;
import ma.youcode.surevote.dto.response.VoteReceiptResponse;
import ma.youcode.surevote.exception.*;
import ma.youcode.surevote.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VoteService (Mockito)")
class VoteServiceMockTest {

    @Mock private ElectionRepository electionRepo;
    @Mock private CandidatRepository candidatRepo;
    @Mock private VoteRepository voteRepo;
    @Mock private EmargementRepository emargementRepo;
    @Mock private UtilisateurRepository utilisateurRepo;
    @InjectMocks private VoteService service;

    private Electeur voter;
    private Election openElection;
    private Candidat candidat;

    @BeforeEach
    void setUp() {
        voter = new Electeur();
        voter.setId(1L);
        voter.setEmail("voter@test.com");
        voter.setNom("Voter");
        voter.setPrenom("Test");
        voter.setRole(RoleUtilisateur.ELECTEUR);
        voter.setEnabled(true);
        voter.setDoubleFacteurActif(false);
        voter.setOtpVerified(true);

        openElection = new Election();
        openElection.setId(100L);
        openElection.setTitre("Test Election");
        openElection.setStatut(StatutElection.OUVERTE);
        openElection.setDateDebut(LocalDateTime.now().minusHours(1));
        openElection.setDateFin(LocalDateTime.now().plusHours(1));

        candidat = new Candidat();
        candidat.setId(200L);
        candidat.setNom("Smith");
        candidat.setPrenom("John");
        candidat.setElection(openElection);

        // Setup security context
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn("voter@test.com");
        SecurityContext sc = mock(SecurityContext.class);
        lenient().when(sc.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(sc);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── verifyReceipt ────────────────────────────────────

    @Test @DisplayName("verifyReceipt success")
    void verifyReceipt_success() {
        Emargement em = new Emargement();
        em.setRecuCryptographique("receipt-uuid");
        em.setElection(openElection);
        em.setDateEmargement(LocalDateTime.now());
        when(emargementRepo.findByRecuCryptographique("receipt-uuid")).thenReturn(Optional.of(em));

        VoteReceiptResponse resp = service.verifyReceipt("receipt-uuid");
        assertThat(resp.getRecuCryptographique()).isEqualTo("receipt-uuid");
        assertThat(resp.getElectionId()).isEqualTo(100L);
    }

    @Test @DisplayName("verifyReceipt — not found throws")
    void verifyReceipt_notFound() {
        when(emargementRepo.findByRecuCryptographique("bad")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verifyReceipt("bad"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── countVotesByElection ─────────────────────────────

    @Test @DisplayName("countVotesByElection delegates")
    void countVotesByElection() {
        when(voteRepo.countByElectionId(1L)).thenReturn(42L);
        assertThat(service.countVotesByElection(1L)).isEqualTo(42L);
    }

    // ── countParticipantsByElection ──────────────────────

    @Test @DisplayName("countParticipantsByElection delegates")
    void countParticipantsByElection() {
        when(emargementRepo.countByElection_Id(1L)).thenReturn(38L);
        assertThat(service.countParticipantsByElection(1L)).isEqualTo(38L);
    }

    // ── getVotedElectionIds ─────────────────────────────

    @Test @DisplayName("getVotedElectionIds returns list")
    void getVotedElectionIds_success() {
        when(utilisateurRepo.findByEmail("voter@test.com")).thenReturn(Optional.of(voter));
        when(emargementRepo.findVotedElectionIdsByElecteur(1L)).thenReturn(List.of(10L, 20L));

        List<Long> ids = service.getVotedElectionIds();
        assertThat(ids).containsExactly(10L, 20L);
    }

    // ── getMyReceipt ────────────────────────────────────

    @Test @DisplayName("getMyReceipt success")
    void getMyReceipt_success() {
        when(utilisateurRepo.findByEmail("voter@test.com")).thenReturn(Optional.of(voter));
        Emargement em = new Emargement();
        em.setRecuCryptographique("my-receipt");
        em.setElection(openElection);
        em.setDateEmargement(LocalDateTime.now());
        when(emargementRepo.findByElecteur_IdAndElection_Id(1L, 100L)).thenReturn(Optional.of(em));
        when(electionRepo.findById(100L)).thenReturn(Optional.of(openElection));
        when(emargementRepo.findByRecuCryptographique("my-receipt")).thenReturn(Optional.of(em));

        VoteReceiptResponse resp = service.getMyReceipt(100L);
        assertThat(resp.getRecuCryptographique()).isEqualTo("my-receipt");
    }

    @Test @DisplayName("getMyReceipt — no vote throws")
    void getMyReceipt_notVoted() {
        when(utilisateurRepo.findByEmail("voter@test.com")).thenReturn(Optional.of(voter));
        when(emargementRepo.findByElecteur_IdAndElection_Id(1L, 100L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getMyReceipt(100L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── checkEligibility ────────────────────────────────

    @Test @DisplayName("checkEligibility — eligible no OTP")
    void checkEligibility_eligible() {
        when(utilisateurRepo.findByEmail("voter@test.com")).thenReturn(Optional.of(voter));
        when(electionRepo.findById(100L)).thenReturn(Optional.of(openElection));
        when(emargementRepo.existsByElecteur_IdAndElection_Id(1L, 100L)).thenReturn(false);

        VoteService.EligibilityResult result = service.checkEligibility(100L);
        assertThat(result.eligible()).isTrue();
        assertThat(result.requiresOtp()).isFalse();
    }

    @Test @DisplayName("checkEligibility — eligible requires OTP")
    void checkEligibility_requiresOtp() {
        voter.setDoubleFacteurActif(true);
        voter.setOtpVerified(false);
        when(utilisateurRepo.findByEmail("voter@test.com")).thenReturn(Optional.of(voter));
        when(electionRepo.findById(100L)).thenReturn(Optional.of(openElection));
        when(emargementRepo.existsByElecteur_IdAndElection_Id(1L, 100L)).thenReturn(false);

        VoteService.EligibilityResult result = service.checkEligibility(100L);
        assertThat(result.eligible()).isTrue();
        assertThat(result.requiresOtp()).isTrue();
    }

    @Test @DisplayName("checkEligibility — election not open")
    void checkEligibility_notOpen() {
        openElection.setStatut(StatutElection.BROUILLON);
        when(utilisateurRepo.findByEmail("voter@test.com")).thenReturn(Optional.of(voter));
        when(electionRepo.findById(100L)).thenReturn(Optional.of(openElection));

        VoteService.EligibilityResult result = service.checkEligibility(100L);
        assertThat(result.eligible()).isFalse();
    }

    @Test @DisplayName("checkEligibility — already voted")
    void checkEligibility_alreadyVoted() {
        when(utilisateurRepo.findByEmail("voter@test.com")).thenReturn(Optional.of(voter));
        when(electionRepo.findById(100L)).thenReturn(Optional.of(openElection));
        when(emargementRepo.existsByElecteur_IdAndElection_Id(1L, 100L)).thenReturn(true);
        when(emargementRepo.findByElecteur_IdAndElection_Id(1L, 100L)).thenReturn(Optional.empty());

        VoteService.EligibilityResult result = service.checkEligibility(100L);
        assertThat(result.eligible()).isFalse();
        assertThat(result.alreadyVoted()).isTrue();
    }

    @Test @DisplayName("checkEligibility — wrong college")
    void checkEligibility_wrongCollege() {
        CollegeElectoral required = CollegeElectoral.builder().id(10L).build();
        openElection.setCollegeElectoral(required);
        voter.setCollegeElectoral(null);

        when(utilisateurRepo.findByEmail("voter@test.com")).thenReturn(Optional.of(voter));
        when(electionRepo.findById(100L)).thenReturn(Optional.of(openElection));
        when(emargementRepo.existsByElecteur_IdAndElection_Id(1L, 100L)).thenReturn(false);

        VoteService.EligibilityResult result = service.checkEligibility(100L);
        assertThat(result.eligible()).isFalse();
    }

    @Test @DisplayName("checkEligibility — different college id")
    void checkEligibility_differentCollege() {
        CollegeElectoral required = CollegeElectoral.builder().id(10L).build();
        CollegeElectoral voterCollege = CollegeElectoral.builder().id(20L).build();
        openElection.setCollegeElectoral(required);
        voter.setCollegeElectoral(voterCollege);

        when(utilisateurRepo.findByEmail("voter@test.com")).thenReturn(Optional.of(voter));
        when(electionRepo.findById(100L)).thenReturn(Optional.of(openElection));
        when(emargementRepo.existsByElecteur_IdAndElection_Id(1L, 100L)).thenReturn(false);

        VoteService.EligibilityResult result = service.checkEligibility(100L);
        assertThat(result.eligible()).isFalse();
    }

    @Test @DisplayName("checkEligibility — election not found throws")
    void checkEligibility_electionNotFound() {
        when(utilisateurRepo.findByEmail("voter@test.com")).thenReturn(Optional.of(voter));
        when(electionRepo.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.checkEligibility(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── verifyIntegrity ─────────────────────────────────

    @Test @DisplayName("verifyIntegrity — all intact")
    void verifyIntegrity_allIntact() {
        Vote v = Vote.builder()
                .id(1L).election(openElection).candidat(candidat)
                .checksumSalt("salt1").build();
        // compute expected checksum
        String expected = sha256("election=100|candidat=200|salt=salt1");
        v.setChecksum(expected);

        when(voteRepo.findAllByElectionIdOrderById(100L)).thenReturn(List.of(v));

        VoteService.IntegrityReport report = service.verifyIntegrity(100L);
        assertThat(report.intact()).isTrue();
        assertThat(report.totalVotes()).isEqualTo(1);
        assertThat(report.corruptedVotes()).isZero();
    }

    @Test @DisplayName("verifyIntegrity — detects corruption")
    void verifyIntegrity_corrupted() {
        Vote v = Vote.builder()
                .id(1L).election(openElection).candidat(candidat)
                .checksumSalt("salt1").checksum("tampered").build();

        when(voteRepo.findAllByElectionIdOrderById(100L)).thenReturn(List.of(v));

        VoteService.IntegrityReport report = service.verifyIntegrity(100L);
        assertThat(report.intact()).isFalse();
        assertThat(report.corruptedVotes()).isEqualTo(1);
    }

    @Test @DisplayName("verifyIntegrity — skips null checksum")
    void verifyIntegrity_nullChecksum() {
        Vote v = Vote.builder()
                .id(1L).election(openElection).candidat(candidat)
                .checksumSalt("salt1").checksum(null).build();

        when(voteRepo.findAllByElectionIdOrderById(100L)).thenReturn(List.of(v));

        VoteService.IntegrityReport report = service.verifyIntegrity(100L);
        assertThat(report.intact()).isTrue();
        assertThat(report.totalVotes()).isEqualTo(1);
    }

    @Test @DisplayName("verifyIntegrity — null salt treated as empty")
    void verifyIntegrity_nullSalt() {
        Vote v = Vote.builder()
                .id(1L).election(openElection).candidat(candidat)
                .checksumSalt(null).build();
        String expected = sha256("election=100|candidat=200|salt=");
        v.setChecksum(expected);

        when(voteRepo.findAllByElectionIdOrderById(100L)).thenReturn(List.of(v));

        VoteService.IntegrityReport report = service.verifyIntegrity(100L);
        assertThat(report.intact()).isTrue();
    }

    @Test @DisplayName("verifyIntegrity — empty list is intact")
    void verifyIntegrity_empty() {
        when(voteRepo.findAllByElectionIdOrderById(100L)).thenReturn(List.of());
        VoteService.IntegrityReport report = service.verifyIntegrity(100L);
        assertThat(report.intact()).isTrue();
        assertThat(report.totalVotes()).isZero();
    }

    // ── IntegrityReport.getSummary ───────────────────────

    @Test @DisplayName("IntegrityReport.getSummary — intact")
    void integrityReport_summaryIntact() {
        VoteService.IntegrityReport report = new VoteService.IntegrityReport(1L, 10, 0, true, LocalDateTime.now());
        assertThat(report.getSummary()).contains("vérifiée").contains("10");
    }

    @Test @DisplayName("IntegrityReport.getSummary — corrupted")
    void integrityReport_summaryCorrupted() {
        VoteService.IntegrityReport report = new VoteService.IntegrityReport(1L, 10, 3, false, LocalDateTime.now());
        assertThat(report.getSummary()).contains("ALERTE").contains("3/10");
    }

    // ── EligibilityResult static helpers ────────────────

    @Test @DisplayName("EligibilityResult.eligible — no OTP message")
    void eligibilityResult_noOtp() {
        VoteService.EligibilityResult er = VoteService.EligibilityResult.eligible(false);
        assertThat(er.eligible()).isTrue();
        assertThat(er.message()).contains("éligible");
    }

    @Test @DisplayName("EligibilityResult.eligible — requires OTP message")
    void eligibilityResult_otp() {
        VoteService.EligibilityResult er = VoteService.EligibilityResult.eligible(true);
        assertThat(er.requiresOtp()).isTrue();
        assertThat(er.message()).contains("2FA");
    }

    @Test @DisplayName("EligibilityResult.notEligible message")
    void eligibilityResult_notEligible() {
        VoteService.EligibilityResult er = VoteService.EligibilityResult.notEligible("reason");
        assertThat(er.eligible()).isFalse();
        assertThat(er.message()).isEqualTo("reason");
    }

    @Test @DisplayName("EligibilityResult.alreadyVoted")
    void eligibilityResult_alreadyVoted() {
        VoteService.EligibilityResult er = VoteService.EligibilityResult.alreadyVoted("voted", "rcpt");
        assertThat(er.alreadyVoted()).isTrue();
        assertThat(er.existingReceipt()).isEqualTo("rcpt");
    }

    // ── resolveAuthenticatedVoter edge cases ─────────────

    @Test @DisplayName("submitVote — non-Electeur user throws VoterNotEligibleException")
    void submitVote_nonElecteur() {
        Administrateur admin = new Administrateur();
        admin.setId(2L);
        admin.setEmail("voter@test.com");
        admin.setRole(RoleUtilisateur.ADMIN);
        when(utilisateurRepo.findByEmail("voter@test.com")).thenReturn(Optional.of(admin));

        VoteRequest req = new VoteRequest();
        req.setElectionId(100L);
        req.setCandidatId(200L);

        assertThatThrownBy(() -> service.submitVote(req))
                .isInstanceOf(VoterNotEligibleException.class);
    }

    @Test @DisplayName("submitVote — disabled voter throws")
    void submitVote_disabledVoter() {
        voter.setEnabled(false);
        when(utilisateurRepo.findByEmail("voter@test.com")).thenReturn(Optional.of(voter));

        VoteRequest req = new VoteRequest();
        req.setElectionId(100L);
        req.setCandidatId(200L);

        assertThatThrownBy(() -> service.submitVote(req))
                .isInstanceOf(VoterNotEligibleException.class);
    }

    @Test @DisplayName("submitVote — user not found throws")
    void submitVote_userNotFound() {
        when(utilisateurRepo.findByEmail("voter@test.com")).thenReturn(Optional.empty());

        VoteRequest req = new VoteRequest();
        req.setElectionId(100L);
        req.setCandidatId(200L);

        assertThatThrownBy(() -> service.submitVote(req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test @DisplayName("submitVote — voter in correct college succeeds")
    void submitVote_voterInCorrectCollege() {
        CollegeElectoral college = CollegeElectoral.builder().id(10L).build();
        openElection.setCollegeElectoral(college);
        voter.setCollegeElectoral(college);

        when(utilisateurRepo.findByEmail("voter@test.com")).thenReturn(Optional.of(voter));
        when(electionRepo.findById(100L)).thenReturn(Optional.of(openElection));
        when(emargementRepo.existsByElecteur_IdAndElection_Id(1L, 100L)).thenReturn(false);
        when(candidatRepo.findByIdAndElectionId(200L, 100L)).thenReturn(Optional.of(candidat));
        when(emargementRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(voteRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        VoteRequest req = new VoteRequest();
        req.setElectionId(100L);
        req.setCandidatId(200L);

        VoteReceiptResponse resp = service.submitVote(req);
        assertThat(resp.getRecuCryptographique()).isNotNull();
    }

    @Test @DisplayName("submitVote — voter in wrong college throws")
    void submitVote_wrongCollege() {
        CollegeElectoral required = CollegeElectoral.builder().id(10L).build();
        CollegeElectoral voterCollege = CollegeElectoral.builder().id(20L).build();
        openElection.setCollegeElectoral(required);
        voter.setCollegeElectoral(voterCollege);

        when(utilisateurRepo.findByEmail("voter@test.com")).thenReturn(Optional.of(voter));
        when(electionRepo.findById(100L)).thenReturn(Optional.of(openElection));

        VoteRequest req = new VoteRequest();
        req.setElectionId(100L);
        req.setCandidatId(200L);

        assertThatThrownBy(() -> service.submitVote(req))
                .isInstanceOf(VoterNotEligibleException.class);
    }

    // ── helper ──────────────────────────────────────────

    private static String sha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(
                    digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
