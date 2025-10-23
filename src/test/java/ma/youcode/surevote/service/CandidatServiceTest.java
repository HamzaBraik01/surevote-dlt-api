package ma.youcode.surevote.service;

import ma.youcode.surevote.domain.entity.Candidat;
import ma.youcode.surevote.domain.entity.Election;
import ma.youcode.surevote.domain.enums.StatutElection;
import ma.youcode.surevote.dto.request.CandidatRequest;
import ma.youcode.surevote.dto.response.CandidatResponse;
import ma.youcode.surevote.exception.DuplicateResourceException;
import ma.youcode.surevote.exception.InvalidElectionStateException;
import ma.youcode.surevote.exception.ResourceNotFoundException;
import ma.youcode.surevote.mapper.CandidatMapper;
import ma.youcode.surevote.repository.CandidatRepository;
import ma.youcode.surevote.repository.ElectionRepository;
import ma.youcode.surevote.repository.VoteRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CandidatService")
class CandidatServiceTest {

    @Mock private CandidatRepository candidatRepository;
    @Mock private ElectionRepository electionRepository;
    @Mock private VoteRepository voteRepository;
    @Mock private CandidatMapper candidatMapper;
    @InjectMocks private CandidatService service;

    private Election brouillon;
    private Election ouverte;
    private Candidat candidat;
    private CandidatResponse resp;
    private CandidatRequest request;

    @BeforeEach
    void setUp() {
        brouillon = Election.builder().id(1L).titre("Test").statut(StatutElection.BROUILLON).build();
        ouverte = Election.builder().id(2L).titre("Open").statut(StatutElection.OUVERTE).build();

        candidat = Candidat.builder().id(10L).nom("Doe").prenom("John")
                .affiliationOuParti("Indep").election(brouillon).build();

        resp = CandidatResponse.builder().id(10L).nom("Doe").prenom("John").build();

        request = new CandidatRequest();
        request.setNom("Doe");
        request.setPrenom("John");
    }

    // ── findAllByElection ──────────────────────────────────────

    @Test @DisplayName("findAllByElection returns mapped candidates")
    void findAllByElection_success() {
        when(electionRepository.existsById(1L)).thenReturn(true);
        when(candidatRepository.findByElectionId(1L)).thenReturn(List.of(candidat));
        when(candidatMapper.toResponse(candidat)).thenReturn(resp);

        List<CandidatResponse> result = service.findAllByElection(1L);
        assertThat(result).containsExactly(resp);
    }

    @Test @DisplayName("findAllByElection throws when election not found")
    void findAllByElection_notFound() {
        when(electionRepository.existsById(99L)).thenReturn(false);
        assertThatThrownBy(() -> service.findAllByElection(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── findById ───────────────────────────────────────────────

    @Test @DisplayName("findById returns mapped candidat")
    void findById_success() {
        when(candidatRepository.findById(10L)).thenReturn(Optional.of(candidat));
        when(candidatMapper.toResponse(candidat)).thenReturn(resp);
        assertThat(service.findById(10L)).isEqualTo(resp);
    }

    @Test @DisplayName("findById throws when not found")
    void findById_notFound() {
        when(candidatRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── countByElection ────────────────────────────────────────

    @Test @DisplayName("countByElection delegates to repo")
    void countByElection() {
        when(candidatRepository.countByElectionId(1L)).thenReturn(5L);
        assertThat(service.countByElection(1L)).isEqualTo(5L);
    }

    // ── addCandidat ────────────────────────────────────────────

    @Test @DisplayName("addCandidat creates candidat in BROUILLON election")
    void addCandidat_success() {
        when(electionRepository.findById(1L)).thenReturn(Optional.of(brouillon));
        when(candidatRepository.existsByNomAndPrenomAndElectionId("Doe", "John", 1L)).thenReturn(false);
        when(candidatMapper.toEntity(request)).thenReturn(candidat);
        when(candidatRepository.save(any())).thenReturn(candidat);
        when(candidatMapper.toResponse(candidat)).thenReturn(resp);

        CandidatResponse result = service.addCandidat(1L, request);
        assertThat(result).isEqualTo(resp);
    }

    @Test @DisplayName("addCandidat throws when election is OUVERTE")
    void addCandidat_electionNotModifiable() {
        when(electionRepository.findById(2L)).thenReturn(Optional.of(ouverte));
        assertThatThrownBy(() -> service.addCandidat(2L, request))
                .isInstanceOf(InvalidElectionStateException.class);
    }

    @Test @DisplayName("addCandidat throws when duplicate name")
    void addCandidat_duplicate() {
        when(electionRepository.findById(1L)).thenReturn(Optional.of(brouillon));
        when(candidatRepository.existsByNomAndPrenomAndElectionId("Doe", "John", 1L)).thenReturn(true);
        assertThatThrownBy(() -> service.addCandidat(1L, request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test @DisplayName("addCandidat throws when election not found")
    void addCandidat_electionNotFound() {
        when(electionRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addCandidat(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── updateCandidat ─────────────────────────────────────────

    @Test @DisplayName("updateCandidat updates successfully when name unchanged")
    void updateCandidat_sameNameSuccess() {
        when(candidatRepository.findById(10L)).thenReturn(Optional.of(candidat));
        doNothing().when(candidatMapper).updateEntity(request, candidat);
        when(candidatRepository.save(any())).thenReturn(candidat);
        when(candidatMapper.toResponse(candidat)).thenReturn(resp);

        CandidatResponse result = service.updateCandidat(10L, request);
        assertThat(result).isEqualTo(resp);
    }

    @Test @DisplayName("updateCandidat checks for duplicate when name changes")
    void updateCandidat_nameChanged_noDuplicate() {
        CandidatRequest newReq = new CandidatRequest();
        newReq.setNom("Smith");
        newReq.setPrenom("Jane");

        when(candidatRepository.findById(10L)).thenReturn(Optional.of(candidat));
        when(candidatRepository.existsByNomAndPrenomAndElectionId("Smith", "Jane", 1L)).thenReturn(false);
        doNothing().when(candidatMapper).updateEntity(newReq, candidat);
        when(candidatRepository.save(any())).thenReturn(candidat);
        when(candidatMapper.toResponse(candidat)).thenReturn(resp);

        assertThatCode(() -> service.updateCandidat(10L, newReq)).doesNotThrowAnyException();
    }

    @Test @DisplayName("updateCandidat throws when election is not modifiable")
    void updateCandidat_electionNotModifiable() {
        candidat.setElection(ouverte);
        when(candidatRepository.findById(10L)).thenReturn(Optional.of(candidat));
        assertThatThrownBy(() -> service.updateCandidat(10L, request))
                .isInstanceOf(InvalidElectionStateException.class);
    }

    @Test @DisplayName("updateCandidat trims photoUrl and programmePdfUrl")
    void updateCandidat_trimsUrls() {
        candidat.setPhotoUrl("  photo.jpg  ");
        candidat.setProgrammePdfUrl("  prog.pdf  ");
        when(candidatRepository.findById(10L)).thenReturn(Optional.of(candidat));
        doNothing().when(candidatMapper).updateEntity(request, candidat);
        when(candidatRepository.save(any())).thenReturn(candidat);
        when(candidatMapper.toResponse(candidat)).thenReturn(resp);

        service.updateCandidat(10L, request);
        verify(candidatRepository).save(argThat(c ->
                c.getPhotoUrl().equals("photo.jpg") && c.getProgrammePdfUrl().equals("prog.pdf")));
    }

    // ── deleteCandidat ─────────────────────────────────────────

    @Test @DisplayName("deleteCandidat removes candidat when no votes")
    void deleteCandidat_success() {
        when(candidatRepository.findById(10L)).thenReturn(Optional.of(candidat));
        when(voteRepository.existsByCandidatId(10L)).thenReturn(false);

        service.deleteCandidat(10L);
        verify(candidatRepository).deleteById(10L);
    }

    @Test @DisplayName("deleteCandidat throws when votes exist")
    void deleteCandidat_hasVotes() {
        when(candidatRepository.findById(10L)).thenReturn(Optional.of(candidat));
        when(voteRepository.existsByCandidatId(10L)).thenReturn(true);
        assertThatThrownBy(() -> service.deleteCandidat(10L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("deleteCandidat throws when election is not modifiable")
    void deleteCandidat_electionNotModifiable() {
        candidat.setElection(ouverte);
        when(candidatRepository.findById(10L)).thenReturn(Optional.of(candidat));
        assertThatThrownBy(() -> service.deleteCandidat(10L))
                .isInstanceOf(InvalidElectionStateException.class);
    }

    // ── updatePhotoUrl ─────────────────────────────────────────

    @Test @DisplayName("updatePhotoUrl sets photo and saves")
    void updatePhotoUrl_success() {
        when(candidatRepository.findById(10L)).thenReturn(Optional.of(candidat));
        when(candidatRepository.save(any())).thenReturn(candidat);
        when(candidatMapper.toResponse(candidat)).thenReturn(resp);

        service.updatePhotoUrl(10L, "/new/photo.jpg");
        verify(candidatRepository).save(argThat(c -> "/new/photo.jpg".equals(c.getPhotoUrl())));
    }

    @Test @DisplayName("updatePhotoUrl throws when election not modifiable")
    void updatePhotoUrl_notModifiable() {
        candidat.setElection(ouverte);
        when(candidatRepository.findById(10L)).thenReturn(Optional.of(candidat));
        assertThatThrownBy(() -> service.updatePhotoUrl(10L, "/photo.jpg"))
                .isInstanceOf(InvalidElectionStateException.class);
    }

    // ── updateProgrammePdfUrl ──────────────────────────────────

    @Test @DisplayName("updateProgrammePdfUrl sets PDF URL and saves")
    void updateProgrammePdfUrl_success() {
        when(candidatRepository.findById(10L)).thenReturn(Optional.of(candidat));
        when(candidatRepository.save(any())).thenReturn(candidat);
        when(candidatMapper.toResponse(candidat)).thenReturn(resp);

        service.updateProgrammePdfUrl(10L, "/new/prog.pdf");
        verify(candidatRepository).save(argThat(c -> "/new/prog.pdf".equals(c.getProgrammePdfUrl())));
    }

    // ── loadCandidatById ───────────────────────────────────────

    @Test @DisplayName("loadCandidatById returns entity")
    void loadCandidatById_found() {
        when(candidatRepository.findById(10L)).thenReturn(Optional.of(candidat));
        assertThat(service.loadCandidatById(10L)).isEqualTo(candidat);
    }

    @Test @DisplayName("loadCandidatById throws when not found")
    void loadCandidatById_notFound() {
        when(candidatRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.loadCandidatById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── loadCandidatForElection ─────────────────────────────────

    @Test @DisplayName("loadCandidatForElection returns entity")
    void loadCandidatForElection_found() {
        when(candidatRepository.findByIdAndElectionId(10L, 1L)).thenReturn(Optional.of(candidat));
        assertThat(service.loadCandidatForElection(10L, 1L)).isEqualTo(candidat);
    }

    @Test @DisplayName("loadCandidatForElection throws when not found")
    void loadCandidatForElection_notFound() {
        when(candidatRepository.findByIdAndElectionId(10L, 99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.loadCandidatForElection(10L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── toResponse ─────────────────────────────────────────────

    @Test @DisplayName("toResponse delegates to mapper")
    void toResponse_delegates() {
        when(candidatMapper.toResponse(candidat)).thenReturn(resp);
        assertThat(service.toResponse(candidat)).isEqualTo(resp);
    }

    // ── toResponseWithResults ──────────────────────────────────

    @Test @DisplayName("toResponseWithResults computes percentage and builds DTO")
    void toResponseWithResults_success() {
        CandidatResponse result = service.toResponseWithResults(candidat, 50, 100, 1);
        assertThat(result.getNombreVotes()).isEqualTo(50);
        assertThat(result.getPourcentageVotes()).isEqualTo(50.0);
        assertThat(result.getRang()).isEqualTo(1);
    }

    @Test @DisplayName("toResponseWithResults handles zero total votes")
    void toResponseWithResults_zeroTotal() {
        CandidatResponse result = service.toResponseWithResults(candidat, 0, 0, 1);
        assertThat(result.getPourcentageVotes()).isEqualTo(0.0);
    }

    @Test @DisplayName("toResponseWithResults handles null election")
    void toResponseWithResults_nullElection() {
        Candidat noElection = Candidat.builder().id(20L).nom("X").prenom("Y").build();
        CandidatResponse result = service.toResponseWithResults(noElection, 10, 50, 2);
        assertThat(result.getElectionId()).isNull();
    }
}
