package ma.youcode.surevote.service;

import ma.youcode.surevote.domain.entity.Election;
import ma.youcode.surevote.domain.entity.CollegeElectoral;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import ma.youcode.surevote.domain.enums.StatutElection;
import ma.youcode.surevote.dto.request.ElectionRequest;
import ma.youcode.surevote.dto.response.ElectionResponse;
import ma.youcode.surevote.exception.InvalidElectionStateException;
import ma.youcode.surevote.exception.ResourceNotFoundException;
import ma.youcode.surevote.mapper.ElectionMapper;
import ma.youcode.surevote.repository.CollegeElectoralRepository;
import ma.youcode.surevote.repository.ElectionRepository;
import ma.youcode.surevote.repository.EmargementRepository;
import ma.youcode.surevote.repository.UtilisateurRepository;
import ma.youcode.surevote.repository.VoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElectionServiceTest {

    @InjectMocks
    private ElectionService electionService;

    @Mock private ElectionRepository electionRepository;
    @Mock private CollegeElectoralRepository collegeElectoralRepository;
    @Mock private EmargementRepository emargementRepository;
    @Mock private VoteRepository voteRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private ElectionMapper electionMapper;

    private ElectionRequest request;

    @BeforeEach
    void setUp() {
        request = ElectionRequest.builder()
                .titre("Election test")
                .description("Desc")
                .dateDebut(LocalDateTime.now().plusDays(1))
                .dateFin(LocalDateTime.now().plusDays(2))
                .build();
    }

    @Test
    void createElection_success_withoutCollege() {
        Election entity = Election.builder()
                .titre(request.getTitre())
                .description(request.getDescription())
                .dateDebut(request.getDateDebut())
                .dateFin(request.getDateFin())
                .build();
        Election saved = Election.builder()
                .id(1L)
                .titre(request.getTitre())
                .description(request.getDescription())
                .dateDebut(request.getDateDebut())
                .dateFin(request.getDateFin())
                .statut(StatutElection.BROUILLON)
                .build();
        ElectionResponse mapped = ElectionResponse.builder()
                .id(1L)
                .titre(request.getTitre())
                .statut(StatutElection.BROUILLON)
                .build();

        when(electionMapper.toEntity(request)).thenReturn(entity);
        when(electionRepository.save(any(Election.class))).thenReturn(saved);
        when(electionMapper.toResponse(saved)).thenReturn(mapped);

        ElectionResponse response = electionService.createElection(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getStatut()).isEqualTo(StatutElection.BROUILLON);
        verify(electionRepository).save(any(Election.class));
    }

    @Test
    void createElection_throws_whenCollegeNotFound() {
        request.setCollegeElectoralId(404L);

        Election entity = Election.builder()
                .titre(request.getTitre())
                .description(request.getDescription())
                .dateDebut(request.getDateDebut())
                .dateFin(request.getDateFin())
                .build();

        when(electionMapper.toEntity(request)).thenReturn(entity);
        when(collegeElectoralRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> electionService.createElection(request));
    }

    @Test
    void createElection_throws_whenDatesInvalid() {
        request.setDateDebut(LocalDateTime.now().plusDays(2));
        request.setDateFin(LocalDateTime.now().plusDays(1));
        assertThrows(IllegalArgumentException.class, () -> electionService.createElection(request));
    }

    @Test
    void findAll_mapsAllItems() {
        Election e = Election.builder().id(1L).titre("E").statut(StatutElection.BROUILLON).build();
        when(electionRepository.findAll()).thenReturn(List.of(e));
        when(electionMapper.toResponse(e)).thenReturn(ElectionResponse.builder().id(1L).build());

        List<ElectionResponse> all = electionService.findAll();
        assertThat(all).hasSize(1);
    }

    @Test
    void findAllPaged_mapsPage() {
        Election e = Election.builder().id(1L).titre("E").statut(StatutElection.BROUILLON).build();
        when(electionRepository.findAll(PageRequest.of(0, 10))).thenReturn(new PageImpl<>(List.of(e)));
        when(electionMapper.toResponse(e)).thenReturn(ElectionResponse.builder().id(1L).build());

        assertThat(electionService.findAll(PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
    }

    @Test
    void findById_throws_whenMissing() {
        when(electionRepository.findByIdWithCandidats(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> electionService.findById(99L));
    }

    @Test
    void search_blank_returnsVisible() {
        Election e = Election.builder().id(4L).titre("Visible").statut(StatutElection.OUVERTE).build();
        when(electionRepository.findAllVisibleToVoters()).thenReturn(List.of(e));
        when(electionMapper.toResponse(e)).thenReturn(ElectionResponse.builder().id(4L).build());

        List<ElectionResponse> responses = electionService.search("  ");
        assertThat(responses).hasSize(1);
    }

    @Test
    void updateElection_throws_whenOpened() {
        Election opened = Election.builder().id(1L).titre("Opened").statut(StatutElection.OUVERTE).build();
        when(electionRepository.findById(1L)).thenReturn(Optional.of(opened));
        assertThrows(InvalidElectionStateException.class, () -> electionService.updateElection(1L, request));
    }

    @Test
    void deleteElection_throws_whenNotDraft() {
        Election published = Election.builder().id(2L).titre("P").statut(StatutElection.PUBLIEE).build();
        when(electionRepository.findById(2L)).thenReturn(Optional.of(published));
        assertThrows(InvalidElectionStateException.class, () -> electionService.deleteElection(2L));
    }

    @Test
    void planifierElection_success() {
        Election draft = Election.builder()
                .id(3L)
                .titre("Draft")
                .statut(StatutElection.BROUILLON)
                .candidats(List.of(new ma.youcode.surevote.domain.entity.Candidat(), new ma.youcode.surevote.domain.entity.Candidat()))
                .build();
        Election planned = Election.builder()
                .id(3L)
                .titre("Draft")
                .statut(StatutElection.PLANIFIEE)
                .candidats(draft.getCandidats())
                .build();

        when(electionRepository.findById(3L)).thenReturn(Optional.of(draft));
        when(electionRepository.save(any(Election.class))).thenReturn(planned);
        when(electionMapper.toResponse(planned)).thenReturn(ElectionResponse.builder().id(3L).statut(StatutElection.PLANIFIEE).build());

        ElectionResponse response = electionService.planifierElection(3L);
        assertThat(response.getStatut()).isEqualTo(StatutElection.PLANIFIEE);
    }

    @Test
    void ouvrirCloturerPublier_transitions_success() {
        Election planned = Election.builder().id(10L).titre("T").statut(StatutElection.PLANIFIEE).build();
        Election opened = Election.builder().id(10L).titre("T").statut(StatutElection.OUVERTE).build();
        Election closed = Election.builder().id(10L).titre("T").statut(StatutElection.CLOTUREE).build();
        Election published = Election.builder().id(10L).titre("T").statut(StatutElection.PUBLIEE).build();

        when(electionRepository.findById(10L))
                .thenReturn(Optional.of(planned))
                .thenReturn(Optional.of(opened))
                .thenReturn(Optional.of(closed));
        when(electionRepository.save(any(Election.class)))
                .thenReturn(opened)
                .thenReturn(closed)
                .thenReturn(published);
        when(electionMapper.toResponse(any(Election.class))).thenAnswer(inv -> {
            Election src = inv.getArgument(0);
            return ElectionResponse.builder().id(src.getId()).statut(src.getStatut()).build();
        });

        assertThat(electionService.ouvrirScrutin(10L).getStatut()).isEqualTo(StatutElection.OUVERTE);
        assertThat(electionService.cloturerScrutin(10L).getStatut()).isEqualTo(StatutElection.CLOTUREE);
        assertThat(electionService.publierResultats(10L).getStatut()).isEqualTo(StatutElection.PUBLIEE);
    }

    @Test
    void toResponse_computesParticipationForClosedElection() {
        CollegeElectoral college = CollegeElectoral.builder().id(99L).nom("College").build();
        Election closed = Election.builder()
                .id(50L)
                .titre("Closed")
                .statut(StatutElection.CLOTUREE)
                .collegeElectoral(college)
                .candidats(List.of(new ma.youcode.surevote.domain.entity.Candidat(), new ma.youcode.surevote.domain.entity.Candidat()))
                .build();

        ElectionResponse mapped = ElectionResponse.builder().id(50L).titre("Closed").statut(StatutElection.CLOTUREE).build();
        when(electionMapper.toResponse(closed)).thenReturn(mapped);
        when(voteRepository.countByElectionId(50L)).thenReturn(8L);
        when(emargementRepository.countByElection_Id(50L)).thenReturn(6L);
        when(collegeElectoralRepository.countMembersByCollegeId(99L)).thenReturn(10L);

        ElectionResponse response = electionService.toResponse(closed, false);
        assertThat(response.getTotalCandidats()).isEqualTo(2);
        assertThat(response.getTotalVotes()).isEqualTo(8L);
        assertThat(response.getTotalParticipants()).isEqualTo(6L);
        assertThat(response.getTotalElecteursEligibles()).isEqualTo(10L);
        assertThat(response.getTauxParticipation()).isEqualTo(60.0);
    }

    @Test
    void toResponse_usesGlobalElecteurs_whenNoCollege() {
        Election closed = Election.builder()
                .id(51L)
                .titre("Closed")
                .statut(StatutElection.CLOTUREE)
                .candidats(List.of())
                .build();
        ElectionResponse mapped = ElectionResponse.builder().id(51L).titre("Closed").statut(StatutElection.CLOTUREE).build();
        when(electionMapper.toResponseWithCandidats(closed)).thenReturn(mapped);
        when(voteRepository.countByElectionId(51L)).thenReturn(1L);
        when(emargementRepository.countByElection_Id(51L)).thenReturn(1L);
        when(utilisateurRepository.countByRole(RoleUtilisateur.ELECTEUR)).thenReturn(4L);

        ElectionResponse response = electionService.toResponse(closed, true);
        assertThat(response.getTauxParticipation()).isEqualTo(25.0);
    }

    @Test
    void countMethods_delegateToRepository() {
        when(electionRepository.countByStatut(StatutElection.OUVERTE)).thenReturn(3L);
        when(electionRepository.count()).thenReturn(11L);
        assertThat(electionService.countByStatut(StatutElection.OUVERTE)).isEqualTo(3L);
        assertThat(electionService.countAll()).isEqualTo(11L);
    }
}
