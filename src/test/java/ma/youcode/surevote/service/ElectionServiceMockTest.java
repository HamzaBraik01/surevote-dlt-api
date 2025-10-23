package ma.youcode.surevote.service;

import ma.youcode.surevote.domain.entity.Candidat;
import ma.youcode.surevote.domain.entity.CollegeElectoral;
import ma.youcode.surevote.domain.entity.Election;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import ma.youcode.surevote.domain.enums.StatutElection;
import ma.youcode.surevote.dto.request.ElectionRequest;
import ma.youcode.surevote.dto.response.ElectionResponse;
import ma.youcode.surevote.exception.InvalidElectionStateException;
import ma.youcode.surevote.exception.ResourceNotFoundException;
import ma.youcode.surevote.mapper.ElectionMapper;
import ma.youcode.surevote.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ElectionService (Mockito)")
class ElectionServiceMockTest {

    @Mock private ElectionRepository electionRepo;
    @Mock private CollegeElectoralRepository collegeRepo;
    @Mock private EmargementRepository emargementRepo;
    @Mock private VoteRepository voteRepo;
    @Mock private UtilisateurRepository utilisateurRepo;
    @Mock private ElectionMapper electionMapper;
    @InjectMocks private ElectionService service;

    private ElectionRequest request;
    private Election draft;

    @BeforeEach
    void setUp() {
        request = ElectionRequest.builder()
                .titre("Test Election")
                .description("Desc")
                .dateDebut(LocalDateTime.now().plusDays(1))
                .dateFin(LocalDateTime.now().plusDays(2))
                .build();

        draft = Election.builder()
                .id(1L)
                .titre("Test Election")
                .statut(StatutElection.BROUILLON)
                .dateDebut(request.getDateDebut())
                .dateFin(request.getDateFin())
                .build();
    }

    // ── createElection ──────────────────────────────────

    @Test @DisplayName("createElection with college")
    void createElection_withCollege() {
        request.setCollegeElectoralId(5L);
        CollegeElectoral college = CollegeElectoral.builder().id(5L).nom("CS").build();

        Election entity = Election.builder().titre("Test Election").build();
        when(electionMapper.toEntity(request)).thenReturn(entity);
        when(collegeRepo.findById(5L)).thenReturn(Optional.of(college));
        when(electionRepo.save(any())).thenReturn(draft);
        when(electionMapper.toResponse(any())).thenReturn(ElectionResponse.builder().id(1L).build());

        ElectionResponse resp = service.createElection(request);
        assertThat(resp.getId()).isEqualTo(1L);
        assertThat(entity.getCollegeElectoral()).isEqualTo(college);
    }

    @Test @DisplayName("createElection — null dates throws")
    void createElection_nullDates() {
        request.setDateDebut(null);
        assertThatThrownBy(() -> service.createElection(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("createElection — past date throws")
    void createElection_pastDate() {
        request.setDateDebut(LocalDateTime.now().minusDays(1));
        assertThatThrownBy(() -> service.createElection(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("createElection — duration too short throws")
    void createElection_tooShort() {
        request.setDateDebut(LocalDateTime.now().plusDays(1));
        request.setDateFin(LocalDateTime.now().plusDays(1).plusMinutes(30));
        assertThatThrownBy(() -> service.createElection(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── updateElection ──────────────────────────────────

    @Test @DisplayName("updateElection success — BROUILLON")
    void updateElection_success() {
        when(electionRepo.findById(1L)).thenReturn(Optional.of(draft));
        when(electionRepo.save(any())).thenReturn(draft);
        when(electionMapper.toResponseWithCandidats(any())).thenReturn(
                ElectionResponse.builder().id(1L).statut(StatutElection.BROUILLON).build());

        ElectionResponse resp = service.updateElection(1L, request);
        assertThat(resp.getStatut()).isEqualTo(StatutElection.BROUILLON);
        verify(electionMapper).updateEntity(request, draft);
    }

    @Test @DisplayName("updateElection success — PLANIFIEE")
    void updateElection_planifiee() {
        draft.setStatut(StatutElection.PLANIFIEE);
        when(electionRepo.findById(1L)).thenReturn(Optional.of(draft));
        when(electionRepo.save(any())).thenReturn(draft);
        when(electionMapper.toResponseWithCandidats(any())).thenReturn(
                ElectionResponse.builder().id(1L).build());

        service.updateElection(1L, request);
        verify(electionMapper).updateEntity(request, draft);
    }

    @Test @DisplayName("updateElection — CLOTUREE throws")
    void updateElection_cloturee() {
        draft.setStatut(StatutElection.CLOTUREE);
        when(electionRepo.findById(1L)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.updateElection(1L, request))
                .isInstanceOf(InvalidElectionStateException.class);
    }

    @Test @DisplayName("updateElection — PUBLIEE throws")
    void updateElection_publiee() {
        draft.setStatut(StatutElection.PUBLIEE);
        when(electionRepo.findById(1L)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.updateElection(1L, request))
                .isInstanceOf(InvalidElectionStateException.class);
    }

    @Test @DisplayName("updateElection — removes college if null id")
    void updateElection_removeCollege() {
        request.setCollegeElectoralId(null);
        draft.setCollegeElectoral(CollegeElectoral.builder().id(5L).build());
        when(electionRepo.findById(1L)).thenReturn(Optional.of(draft));
        when(electionRepo.save(any())).thenReturn(draft);
        when(electionMapper.toResponseWithCandidats(any())).thenReturn(
                ElectionResponse.builder().id(1L).build());

        service.updateElection(1L, request);
        assertThat(draft.getCollegeElectoral()).isNull();
    }

    @Test @DisplayName("updateElection — sets college if id provided")
    void updateElection_setCollege() {
        request.setCollegeElectoralId(7L);
        CollegeElectoral col = CollegeElectoral.builder().id(7L).build();
        when(electionRepo.findById(1L)).thenReturn(Optional.of(draft));
        when(collegeRepo.findById(7L)).thenReturn(Optional.of(col));
        when(electionRepo.save(any())).thenReturn(draft);
        when(electionMapper.toResponseWithCandidats(any())).thenReturn(
                ElectionResponse.builder().id(1L).build());

        service.updateElection(1L, request);
        assertThat(draft.getCollegeElectoral()).isEqualTo(col);
    }

    // ── deleteElection ──────────────────────────────────

    @Test @DisplayName("deleteElection success")
    void deleteElection_success() {
        when(electionRepo.findById(1L)).thenReturn(Optional.of(draft));
        when(voteRepo.existsByElectionId(1L)).thenReturn(false);
        service.deleteElection(1L);
        verify(electionRepo).deleteById(1L);
    }

    @Test @DisplayName("deleteElection — has votes throws")
    void deleteElection_hasVotes() {
        when(electionRepo.findById(1L)).thenReturn(Optional.of(draft));
        when(voteRepo.existsByElectionId(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.deleteElection(1L))
                .isInstanceOf(InvalidElectionStateException.class);
    }

    // ── search ──────────────────────────────────────────

    @Test @DisplayName("search with keyword")
    void search_withKeyword() {
        Election e = Election.builder().id(1L).titre("Matching").statut(StatutElection.OUVERTE).build();
        when(electionRepo.searchByKeyword("match")).thenReturn(List.of(e));
        when(electionMapper.toResponse(e)).thenReturn(ElectionResponse.builder().id(1L).build());

        List<ElectionResponse> result = service.search("match");
        assertThat(result).hasSize(1);
    }

    @Test @DisplayName("search null keyword returns visible")
    void search_null() {
        when(electionRepo.findAllVisibleToVoters()).thenReturn(Collections.emptyList());
        List<ElectionResponse> result = service.search(null);
        assertThat(result).isEmpty();
    }

    // ── findById ────────────────────────────────────────

    @Test @DisplayName("findById success")
    void findById_success() {
        when(electionRepo.findByIdWithCandidats(1L)).thenReturn(Optional.of(draft));
        when(electionMapper.toResponseWithCandidats(draft)).thenReturn(
                ElectionResponse.builder().id(1L).build());

        ElectionResponse resp = service.findById(1L);
        assertThat(resp.getId()).isEqualTo(1L);
    }

    // ── findAllVisible / findAllOpen / findAllPublished / findByStatut ──

    @Test @DisplayName("findAllVisible delegates")
    void findAllVisible() {
        Election e = Election.builder().id(1L).statut(StatutElection.OUVERTE).build();
        when(electionRepo.findAllVisibleToVoters()).thenReturn(List.of(e));
        when(electionMapper.toResponse(e)).thenReturn(ElectionResponse.builder().id(1L).build());
        assertThat(service.findAllVisible()).hasSize(1);
    }

    @Test @DisplayName("findAllOpen delegates")
    void findAllOpen() {
        Election e = Election.builder().id(1L).statut(StatutElection.OUVERTE).build();
        when(electionRepo.findAllOpenElections()).thenReturn(List.of(e));
        when(electionMapper.toResponse(e)).thenReturn(ElectionResponse.builder().id(1L).build());
        assertThat(service.findAllOpen()).hasSize(1);
    }

    @Test @DisplayName("findAllPublished delegates")
    void findAllPublished() {
        Election e = Election.builder().id(1L).statut(StatutElection.PUBLIEE).build();
        when(electionRepo.findAllPublishedElections()).thenReturn(List.of(e));
        when(electionMapper.toResponse(e)).thenReturn(ElectionResponse.builder().id(1L).build());
        assertThat(service.findAllPublished()).hasSize(1);
    }

    @Test @DisplayName("findByStatut delegates")
    void findByStatut() {
        Election e = Election.builder().id(1L).statut(StatutElection.PLANIFIEE).build();
        when(electionRepo.findAllByStatutOrderByDateDebutDesc(StatutElection.PLANIFIEE)).thenReturn(List.of(e));
        when(electionMapper.toResponse(e)).thenReturn(ElectionResponse.builder().id(1L).build());
        assertThat(service.findByStatut(StatutElection.PLANIFIEE)).hasSize(1);
    }

    @Test @DisplayName("findEligibleForVoter delegates")
    void findEligibleForVoter() {
        Election e = Election.builder().id(1L).statut(StatutElection.OUVERTE).build();
        when(electionRepo.findEligibleElectionsForVoter(1L)).thenReturn(List.of(e));
        when(electionMapper.toResponse(e)).thenReturn(ElectionResponse.builder().id(1L).build());
        assertThat(service.findEligibleForVoter(1L)).hasSize(1);
    }

    // ── loadElectionOrThrow ────────────────────────────

    @Test @DisplayName("loadElectionOrThrow success")
    void loadElectionOrThrow_success() {
        when(electionRepo.findById(1L)).thenReturn(Optional.of(draft));
        assertThat(service.loadElectionOrThrow(1L)).isEqualTo(draft);
    }

    @Test @DisplayName("loadElectionOrThrow — not found throws")
    void loadElectionOrThrow_notFound() {
        when(electionRepo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.loadElectionOrThrow(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── planifierElection edge cases ────────────────────

    @Test @DisplayName("planifierElection — not BROUILLON throws")
    void planifier_notBrouillon() {
        draft.setStatut(StatutElection.OUVERTE);
        when(electionRepo.findById(1L)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.planifierElection(1L))
                .isInstanceOf(InvalidElectionStateException.class);
    }

    @Test @DisplayName("planifierElection — less than 2 candidates throws")
    void planifier_tooFewCandidates() {
        draft.setCandidats(List.of(new Candidat()));
        when(electionRepo.findById(1L)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.planifierElection(1L))
                .isInstanceOf(InvalidElectionStateException.class);
    }

    @Test @DisplayName("planifierElection — null candidates throws")
    void planifier_nullCandidates() {
        draft.setCandidats(null);
        when(electionRepo.findById(1L)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.planifierElection(1L))
                .isInstanceOf(InvalidElectionStateException.class);
    }

    @Test @DisplayName("planifierElection — too many candidates throws")
    void planifier_tooManyCandidates() {
        List<Candidat> many = new java.util.ArrayList<>(Collections.nCopies(21, new Candidat()));
        draft.setCandidats(many);
        when(electionRepo.findById(1L)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.planifierElection(1L))
                .isInstanceOf(InvalidElectionStateException.class);
    }

    // ── ouvrirScrutin edge case ─────────────────────────

    @Test @DisplayName("ouvrirScrutin — not PLANIFIEE throws")
    void ouvrir_notPlanifiee() {
        draft.setStatut(StatutElection.BROUILLON);
        when(electionRepo.findById(1L)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.ouvrirScrutin(1L))
                .isInstanceOf(InvalidElectionStateException.class);
    }

    // ── cloturerScrutin edge case ───────────────────────

    @Test @DisplayName("cloturerScrutin — not OUVERTE throws")
    void cloturer_notOuverte() {
        draft.setStatut(StatutElection.PLANIFIEE);
        when(electionRepo.findById(1L)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.cloturerScrutin(1L))
                .isInstanceOf(InvalidElectionStateException.class);
    }

    // ── publierResultats edge case ─────────────────────

    @Test @DisplayName("publierResultats — not CLOTUREE throws")
    void publier_notCloturee() {
        draft.setStatut(StatutElection.OUVERTE);
        when(electionRepo.findById(1L)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.publierResultats(1L))
                .isInstanceOf(InvalidElectionStateException.class);
    }

    // ── toResponse — participation metrics ──────────────

    @Test @DisplayName("toResponse — BROUILLON skips participation")
    void toResponse_brouillon() {
        when(electionMapper.toResponse(draft)).thenReturn(ElectionResponse.builder().id(1L).build());
        ElectionResponse resp = service.toResponse(draft, false);
        assertThat(resp.getTotalVotes()).isNull();
    }

    @Test @DisplayName("toResponse — zero eligibles returns 0% participation")
    void toResponse_zeroEligibles() {
        draft.setStatut(StatutElection.CLOTUREE);
        when(electionMapper.toResponse(draft)).thenReturn(ElectionResponse.builder().id(1L).build());
        when(voteRepo.countByElectionId(1L)).thenReturn(5L);
        when(emargementRepo.countByElection_Id(1L)).thenReturn(5L);
        when(utilisateurRepo.countByRole(RoleUtilisateur.ELECTEUR)).thenReturn(0L);

        ElectionResponse resp = service.toResponse(draft, false);
        assertThat(resp.getTauxParticipation()).isEqualTo(0.0);
    }

    @Test @DisplayName("toResponse — null candidats shows 0")
    void toResponse_nullCandidats() {
        draft.setStatut(StatutElection.PUBLIEE);
        draft.setCandidats(null);
        when(electionMapper.toResponse(draft)).thenReturn(ElectionResponse.builder().id(1L).build());
        when(voteRepo.countByElectionId(1L)).thenReturn(0L);
        when(emargementRepo.countByElection_Id(1L)).thenReturn(0L);
        when(utilisateurRepo.countByRole(RoleUtilisateur.ELECTEUR)).thenReturn(10L);

        ElectionResponse resp = service.toResponse(draft, false);
        assertThat(resp.getTotalCandidats()).isZero();
    }
}
