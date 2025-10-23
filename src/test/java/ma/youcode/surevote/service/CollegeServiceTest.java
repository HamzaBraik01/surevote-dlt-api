package ma.youcode.surevote.service;

import ma.youcode.surevote.domain.entity.*;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import ma.youcode.surevote.domain.enums.StatutElection;
import ma.youcode.surevote.dto.request.CollegeRequest;
import ma.youcode.surevote.dto.response.CollegeResponse;
import ma.youcode.surevote.dto.response.UserResponse;
import ma.youcode.surevote.exception.DuplicateResourceException;
import ma.youcode.surevote.exception.InvalidElectionStateException;
import ma.youcode.surevote.exception.ResourceNotFoundException;
import ma.youcode.surevote.mapper.CollegeElectoralMapper;
import ma.youcode.surevote.repository.CollegeElectoralRepository;
import ma.youcode.surevote.repository.UtilisateurRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CollegeService")
class CollegeServiceTest {

    @Mock private CollegeElectoralRepository collegeRepo;
    @Mock private UtilisateurRepository utilisateurRepo;
    @Mock private UserService userService;
    @Mock private CollegeElectoralMapper collegeMapper;
    @InjectMocks private CollegeService service;

    private CollegeElectoral college;
    private CollegeResponse collegeResp;
    private CollegeRequest collegeReq;
    private Electeur electeur;

    @BeforeEach
    void setUp() {
        college = CollegeElectoral.builder().id(1L).nom("CS Students").description("Desc")
                .electeurs(new ArrayList<>()).elections(new ArrayList<>()).build();
        collegeResp = CollegeResponse.builder().id(1L).nom("CS Students").build();
        collegeReq = new CollegeRequest();
        collegeReq.setNom("CS Students");

        electeur = new Electeur();
        electeur.setId(10L);
        electeur.setRole(RoleUtilisateur.ELECTEUR);
        electeur.setNom("Doe");
        electeur.setPrenom("John");
        electeur.setEmail("john@test.com");
    }

    // ── Read operations ────────────────────────────────────────

    @Test @DisplayName("findAll returns all colleges mapped")
    void findAll() {
        when(collegeRepo.findAll()).thenReturn(List.of(college));
        when(collegeMapper.toResponse(college)).thenReturn(collegeResp);
        assertThat(service.findAll()).containsExactly(collegeResp);
    }

    @Test @DisplayName("findAll pageable returns paged results")
    void findAll_pageable() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<CollegeElectoral> page = new PageImpl<>(List.of(college));
        when(collegeRepo.findAll(pageable)).thenReturn(page);
        when(collegeMapper.toResponse(college)).thenReturn(collegeResp);

        Page<CollegeResponse> result = service.findAll(pageable);
        assertThat(result.getContent()).containsExactly(collegeResp);
    }

    @Test @DisplayName("findById returns mapped college")
    void findById() {
        when(collegeRepo.findById(1L)).thenReturn(Optional.of(college));
        when(collegeMapper.toResponse(college)).thenReturn(collegeResp);
        assertThat(service.findById(1L)).isEqualTo(collegeResp);
    }

    @Test @DisplayName("findById throws when not found")
    void findById_notFound() {
        when(collegeRepo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test @DisplayName("findMembersByCollegeId returns mapped members")
    void findMembersByCollegeId() {
        college.getElecteurs().add(electeur);
        when(collegeRepo.findById(1L)).thenReturn(Optional.of(college));
        UserResponse userResp = UserResponse.builder().id(10L).build();
        when(userService.toResponse(electeur)).thenReturn(userResp);

        List<UserResponse> result = service.findMembersByCollegeId(1L);
        assertThat(result).hasSize(1);
    }

    @Test @DisplayName("search with keyword delegates to repo")
    void search_withKeyword() {
        when(collegeRepo.findByNomContainingIgnoreCase("CS")).thenReturn(List.of(college));
        when(collegeMapper.toResponse(college)).thenReturn(collegeResp);
        assertThat(service.search("CS")).containsExactly(collegeResp);
    }

    @Test @DisplayName("search with null returns all")
    void search_null() {
        when(collegeRepo.findAll()).thenReturn(List.of(college));
        when(collegeMapper.toResponse(college)).thenReturn(collegeResp);
        assertThat(service.search(null)).containsExactly(collegeResp);
    }

    @Test @DisplayName("search with blank returns all")
    void search_blank() {
        when(collegeRepo.findAll()).thenReturn(List.of(college));
        when(collegeMapper.toResponse(college)).thenReturn(collegeResp);
        assertThat(service.search("   ")).containsExactly(collegeResp);
    }

    @Test @DisplayName("countMembers delegates to repo")
    void countMembers() {
        when(collegeRepo.countMembersByCollegeId(1L)).thenReturn(42L);
        assertThat(service.countMembers(1L)).isEqualTo(42L);
    }

    // ── Create ─────────────────────────────────────────────────

    @Test @DisplayName("create stores new college")
    void create_success() {
        when(collegeRepo.existsByNom("CS Students")).thenReturn(false);
        when(collegeMapper.toEntity(collegeReq)).thenReturn(college);
        when(collegeRepo.save(any())).thenReturn(college);
        when(collegeMapper.toResponse(college)).thenReturn(collegeResp);

        assertThat(service.create(collegeReq)).isEqualTo(collegeResp);
    }

    @Test @DisplayName("create trims description when not null")
    void create_trimsDescription() {
        college.setDescription("  trimmed  ");
        when(collegeRepo.existsByNom("CS Students")).thenReturn(false);
        when(collegeMapper.toEntity(collegeReq)).thenReturn(college);
        when(collegeRepo.save(any())).thenReturn(college);
        when(collegeMapper.toResponse(college)).thenReturn(collegeResp);

        service.create(collegeReq);
        verify(collegeRepo).save(argThat(c -> "trimmed".equals(c.getDescription())));
    }

    @Test @DisplayName("create handles null description")
    void create_nullDescription() {
        college.setDescription(null);
        when(collegeRepo.existsByNom("CS Students")).thenReturn(false);
        when(collegeMapper.toEntity(collegeReq)).thenReturn(college);
        when(collegeRepo.save(any())).thenReturn(college);
        when(collegeMapper.toResponse(college)).thenReturn(collegeResp);

        service.create(collegeReq);
        verify(collegeRepo).save(argThat(c -> c.getDescription() == null));
    }

    @Test @DisplayName("create throws on duplicate name")
    void create_duplicate() {
        when(collegeRepo.existsByNom("CS Students")).thenReturn(true);
        assertThatThrownBy(() -> service.create(collegeReq))
                .isInstanceOf(DuplicateResourceException.class);
    }

    // ── Update ─────────────────────────────────────────────────

    @Test @DisplayName("update with same name succeeds")
    void update_sameName() {
        when(collegeRepo.findById(1L)).thenReturn(Optional.of(college));
        doNothing().when(collegeMapper).updateEntity(collegeReq, college);
        when(collegeRepo.save(any())).thenReturn(college);
        when(collegeMapper.toResponse(college)).thenReturn(collegeResp);

        assertThat(service.update(1L, collegeReq)).isEqualTo(collegeResp);
    }

    @Test @DisplayName("update with different name checks uniqueness")
    void update_differentName_noDuplicate() {
        CollegeRequest newReq = new CollegeRequest();
        newReq.setNom("New Name");
        when(collegeRepo.findById(1L)).thenReturn(Optional.of(college));
        when(collegeRepo.existsByNom("New Name")).thenReturn(false);
        doNothing().when(collegeMapper).updateEntity(newReq, college);
        when(collegeRepo.save(any())).thenReturn(college);
        when(collegeMapper.toResponse(college)).thenReturn(collegeResp);

        assertThatCode(() -> service.update(1L, newReq)).doesNotThrowAnyException();
    }

    @Test @DisplayName("update with different name throws on duplicate")
    void update_differentName_duplicate() {
        CollegeRequest newReq = new CollegeRequest();
        newReq.setNom("Taken");
        when(collegeRepo.findById(1L)).thenReturn(Optional.of(college));
        when(collegeRepo.existsByNom("Taken")).thenReturn(true);
        assertThatThrownBy(() -> service.update(1L, newReq))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test @DisplayName("update trims description when not null")
    void update_trimsDescription() {
        college.setDescription("  trimmed  ");
        when(collegeRepo.findById(1L)).thenReturn(Optional.of(college));
        doNothing().when(collegeMapper).updateEntity(collegeReq, college);
        when(collegeRepo.save(any())).thenReturn(college);
        when(collegeMapper.toResponse(college)).thenReturn(collegeResp);

        service.update(1L, collegeReq);
        verify(collegeRepo).save(argThat(c -> "trimmed".equals(c.getDescription())));
    }

    // ── Delete ─────────────────────────────────────────────────

    @Test @DisplayName("delete removes college and unlinks voters/elections")
    void delete_success() {
        college.getElecteurs().add(electeur);
        electeur.setCollegeElectoral(college);

        Election cloturee = Election.builder().id(5L).statut(StatutElection.CLOTUREE).build();
        cloturee.setCollegeElectoral(college);
        college.getElections().add(cloturee);

        when(collegeRepo.findById(1L)).thenReturn(Optional.of(college));

        service.delete(1L);

        assertThat(electeur.getCollegeElectoral()).isNull();
        assertThat(cloturee.getCollegeElectoral()).isNull();
        verify(collegeRepo).delete(college);
    }

    @Test @DisplayName("delete throws when linked election is OUVERTE")
    void delete_activeElection() {
        Election ouverte = Election.builder().id(5L).titre("Open").statut(StatutElection.OUVERTE).build();
        college.getElections().add(ouverte);
        when(collegeRepo.findById(1L)).thenReturn(Optional.of(college));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(InvalidElectionStateException.class);
    }

    @Test @DisplayName("delete throws when linked election is PLANIFIEE")
    void delete_planifieeElection() {
        Election planifiee = Election.builder().id(5L).titre("Planned").statut(StatutElection.PLANIFIEE).build();
        college.getElections().add(planifiee);
        when(collegeRepo.findById(1L)).thenReturn(Optional.of(college));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(InvalidElectionStateException.class);
    }

    // ── addVoterToCollege ──────────────────────────────────────

    @Test @DisplayName("addVoterToCollege adds voter successfully")
    void addVoter_success() {
        when(collegeRepo.findById(1L)).thenReturn(Optional.of(college));
        when(utilisateurRepo.findById(10L)).thenReturn(Optional.of(electeur));
        when(utilisateurRepo.save(any())).thenReturn(electeur);
        when(collegeRepo.save(any())).thenReturn(college);
        when(collegeMapper.toResponse(college)).thenReturn(collegeResp);

        assertThat(service.addVoterToCollege(1L, 10L)).isEqualTo(collegeResp);
    }

    @Test @DisplayName("addVoterToCollege returns idempotent when already in college")
    void addVoter_alreadyInCollege() {
        electeur.setCollegeElectoral(college);
        when(collegeRepo.findById(1L)).thenReturn(Optional.of(college));
        when(utilisateurRepo.findById(10L)).thenReturn(Optional.of(electeur));
        when(collegeMapper.toResponse(college)).thenReturn(collegeResp);

        assertThat(service.addVoterToCollege(1L, 10L)).isEqualTo(collegeResp);
        verify(utilisateurRepo, never()).save(any());
    }

    @Test @DisplayName("addVoterToCollege moves voter from previous college")
    void addVoter_movesFromPrevious() {
        CollegeElectoral previous = CollegeElectoral.builder().id(2L).nom("Old")
                .electeurs(new ArrayList<>(List.of(electeur))).build();
        electeur.setCollegeElectoral(previous);

        when(collegeRepo.findById(1L)).thenReturn(Optional.of(college));
        when(utilisateurRepo.findById(10L)).thenReturn(Optional.of(electeur));
        when(collegeRepo.save(any())).thenReturn(college);
        when(utilisateurRepo.save(any())).thenReturn(electeur);
        when(collegeMapper.toResponse(college)).thenReturn(collegeResp);

        service.addVoterToCollege(1L, 10L);
        verify(collegeRepo, times(2)).save(any()); // save previous + save current
    }

    @Test @DisplayName("addVoterToCollege throws when user is not ELECTEUR")
    void addVoter_notElecteur() {
        Administrateur admin = new Administrateur();
        admin.setId(20L);
        admin.setRole(RoleUtilisateur.ADMIN);
        when(collegeRepo.findById(1L)).thenReturn(Optional.of(college));
        when(utilisateurRepo.findById(20L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.addVoterToCollege(1L, 20L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("addVoterToCollege throws when user not found")
    void addVoter_userNotFound() {
        when(collegeRepo.findById(1L)).thenReturn(Optional.of(college));
        when(utilisateurRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addVoterToCollege(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── removeVoterFromCollege ──────────────────────────────────

    @Test @DisplayName("removeVoterFromCollege removes voter successfully")
    void removeVoter_success() {
        electeur.setCollegeElectoral(college);
        college.getElecteurs().add(electeur);
        when(collegeRepo.findById(1L)).thenReturn(Optional.of(college));
        when(utilisateurRepo.findById(10L)).thenReturn(Optional.of(electeur));
        when(utilisateurRepo.save(any())).thenReturn(electeur);
        when(collegeRepo.save(any())).thenReturn(college);
        when(collegeMapper.toResponse(college)).thenReturn(collegeResp);

        assertThat(service.removeVoterFromCollege(1L, 10L)).isEqualTo(collegeResp);
    }

    @Test @DisplayName("removeVoterFromCollege no-op when voter not in college")
    void removeVoter_notInCollege() {
        electeur.setCollegeElectoral(null);
        when(collegeRepo.findById(1L)).thenReturn(Optional.of(college));
        when(utilisateurRepo.findById(10L)).thenReturn(Optional.of(electeur));
        when(collegeMapper.toResponse(college)).thenReturn(collegeResp);

        service.removeVoterFromCollege(1L, 10L);
        verify(utilisateurRepo, never()).save(any());
    }

    @Test @DisplayName("removeVoterFromCollege no-op when voter in different college")
    void removeVoter_differentCollege() {
        CollegeElectoral other = CollegeElectoral.builder().id(99L).build();
        electeur.setCollegeElectoral(other);
        when(collegeRepo.findById(1L)).thenReturn(Optional.of(college));
        when(utilisateurRepo.findById(10L)).thenReturn(Optional.of(electeur));
        when(collegeMapper.toResponse(college)).thenReturn(collegeResp);

        service.removeVoterFromCollege(1L, 10L);
        verify(utilisateurRepo, never()).save(any());
    }

    @Test @DisplayName("removeVoterFromCollege throws when user is not ELECTEUR")
    void removeVoter_notElecteur() {
        Administrateur admin = new Administrateur();
        admin.setId(20L);
        admin.setRole(RoleUtilisateur.ADMIN);
        when(collegeRepo.findById(1L)).thenReturn(Optional.of(college));
        when(utilisateurRepo.findById(20L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.removeVoterFromCollege(1L, 20L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── isVoterInCollege ───────────────────────────────────────

    @Test @DisplayName("isVoterInCollege delegates to repo")
    void isVoterInCollege() {
        when(collegeRepo.isElecteurInCollege(1L, 10L)).thenReturn(true);
        assertThat(service.isVoterInCollege(1L, 10L)).isTrue();
    }

    // ── Internal helpers ───────────────────────────────────────

    @Test @DisplayName("findEntityById returns entity")
    void findEntityById_found() {
        when(collegeRepo.findById(1L)).thenReturn(Optional.of(college));
        assertThat(service.findEntityById(1L)).isEqualTo(college);
    }

    @Test @DisplayName("findEntityById throws when not found")
    void findEntityById_notFound() {
        when(collegeRepo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findEntityById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test @DisplayName("toResponse delegates to mapper")
    void toResponse() {
        when(collegeMapper.toResponse(college)).thenReturn(collegeResp);
        assertThat(service.toResponse(college)).isEqualTo(collegeResp);
    }
}
