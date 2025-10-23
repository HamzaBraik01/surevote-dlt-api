package ma.youcode.surevote.service;

import ma.youcode.surevote.domain.entity.Administrateur;
import ma.youcode.surevote.domain.entity.Electeur;
import ma.youcode.surevote.domain.entity.Observateur;
import ma.youcode.surevote.domain.entity.Utilisateur;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import ma.youcode.surevote.dto.request.CreateUserRequest;
import ma.youcode.surevote.dto.response.UserResponse;
import ma.youcode.surevote.exception.DuplicateResourceException;
import ma.youcode.surevote.exception.ResourceNotFoundException;
import ma.youcode.surevote.mapper.UtilisateurMapper;
import ma.youcode.surevote.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for {@link UserService}.
 * Targets 100% instruction and branch coverage.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UtilisateurMapper utilisateurMapper;

    @InjectMocks
    private UserService userService;

    // Reusable fixtures
    private Electeur electeur;
    private Administrateur admin;
    private Observateur observateur;
    private UserResponse dummyResponse;

    @BeforeEach
    void setUp() {
        electeur = new Electeur();
        electeur.setId(1L);
        electeur.setCin("AB123456");
        electeur.setNom("Dupont");
        electeur.setPrenom("Jean");
        electeur.setEmail("jean@example.com");
        electeur.setMotDePasse("hashed");
        electeur.setRole(RoleUtilisateur.ELECTEUR);
        electeur.setEnabled(true);

        admin = new Administrateur();
        admin.setId(2L);
        admin.setCin("CD789012");
        admin.setNom("Admin");
        admin.setPrenom("Super");
        admin.setEmail("admin@example.com");
        admin.setMotDePasse("hashed");
        admin.setRole(RoleUtilisateur.ADMIN);
        admin.setEnabled(true);
        admin.setDepartement("IT");

        observateur = new Observateur();
        observateur.setId(3L);
        observateur.setCin("EF345678");
        observateur.setNom("Obs");
        observateur.setPrenom("Viewer");
        observateur.setEmail("obs@example.com");
        observateur.setMotDePasse("hashed");
        observateur.setRole(RoleUtilisateur.OBSERVATEUR);
        observateur.setEnabled(true);
        observateur.setOrganisme("AuditOrg");

        dummyResponse = UserResponse.builder()
                .id(1L)
                .cin("AB123456")
                .nom("Dupont")
                .prenom("Jean")
                .email("jean@example.com")
                .role(RoleUtilisateur.ELECTEUR)
                .isEnabled(true)
                .build();
    }

    // ================================================================
    // Helper to build a CreateUserRequest
    // ================================================================

    private CreateUserRequest.CreateUserRequestBuilder baseRequest() {
        return CreateUserRequest.builder()
                .cin("AB123456")
                .nom("Dupont")
                .prenom("Jean")
                .email("jean@example.com");
    }

    // ================================================================
    // createUser tests
    // ================================================================

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("creates ADMIN user with random password and sends email")
        void createUser_Admin_RandomPassword() {
            CreateUserRequest req = baseRequest()
                    .role(RoleUtilisateur.ADMIN)
                    .departement("IT")
                    .generateRandomPassword(true)
                    .build();

            when(utilisateurRepository.existsByEmail(anyString())).thenReturn(false);
            when(utilisateurRepository.existsByCin(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(inv -> {
                Utilisateur u = inv.getArgument(0);
                u.setId(10L);
                return u;
            });
            when(utilisateurMapper.toResponse(any(Utilisateur.class))).thenReturn(dummyResponse);

            UserResponse result = userService.createUser(req);

            assertThat(result).isNotNull();
            ArgumentCaptor<Utilisateur> captor = ArgumentCaptor.forClass(Utilisateur.class);
            verify(utilisateurRepository).save(captor.capture());
            Utilisateur saved = captor.getValue();
            assertThat(saved).isInstanceOf(Administrateur.class);
            assertThat(((Administrateur) saved).getDepartement()).isEqualTo("IT");
            assertThat(saved.getMotDePasse()).isEqualTo("encoded");
            assertThat(saved.getRole()).isEqualTo(RoleUtilisateur.ADMIN);
            verify(notificationService).sendEmail(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("creates OBSERVATEUR user with random password")
        void createUser_Observateur() {
            CreateUserRequest req = baseRequest()
                    .role(RoleUtilisateur.OBSERVATEUR)
                    .organisme("AuditOrg")
                    .generateRandomPassword(true)
                    .build();

            when(utilisateurRepository.existsByEmail(anyString())).thenReturn(false);
            when(utilisateurRepository.existsByCin(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(inv -> {
                Utilisateur u = inv.getArgument(0);
                u.setId(11L);
                return u;
            });
            when(utilisateurMapper.toResponse(any(Utilisateur.class))).thenReturn(dummyResponse);

            UserResponse result = userService.createUser(req);

            assertThat(result).isNotNull();
            ArgumentCaptor<Utilisateur> captor = ArgumentCaptor.forClass(Utilisateur.class);
            verify(utilisateurRepository).save(captor.capture());
            assertThat(captor.getValue()).isInstanceOf(Observateur.class);
            assertThat(((Observateur) captor.getValue()).getOrganisme()).isEqualTo("AuditOrg");
        }

        @Test
        @DisplayName("creates ELECTEUR user (default branch) with random password")
        void createUser_Electeur_Default() {
            CreateUserRequest req = baseRequest()
                    .role(RoleUtilisateur.ELECTEUR)
                    .telephone("+212600000000")
                    .doubleFacteurActif(true)
                    .generateRandomPassword(true)
                    .build();

            when(utilisateurRepository.existsByEmail(anyString())).thenReturn(false);
            when(utilisateurRepository.existsByCin(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(inv -> {
                Utilisateur u = inv.getArgument(0);
                u.setId(12L);
                return u;
            });
            when(utilisateurMapper.toResponse(any(Utilisateur.class))).thenReturn(dummyResponse);

            UserResponse result = userService.createUser(req);

            assertThat(result).isNotNull();
            ArgumentCaptor<Utilisateur> captor = ArgumentCaptor.forClass(Utilisateur.class);
            verify(utilisateurRepository).save(captor.capture());
            Utilisateur saved = captor.getValue();
            assertThat(saved).isInstanceOf(Electeur.class);
            assertThat(((Electeur) saved).getTelephone()).isEqualTo("+212600000000");
            assertThat(((Electeur) saved).isDoubleFacteurActif()).isTrue();
        }

        @Test
        @DisplayName("uses provided password when generateRandomPassword is false and motDePasse is set")
        void createUser_ProvidedPassword_NoEmail() {
            CreateUserRequest req = baseRequest()
                    .role(RoleUtilisateur.ELECTEUR)
                    .motDePasse("MyP@ssword1")
                    .generateRandomPassword(false)
                    .build();

            when(utilisateurRepository.existsByEmail(anyString())).thenReturn(false);
            when(utilisateurRepository.existsByCin(anyString())).thenReturn(false);
            when(passwordEncoder.encode("MyP@ssword1")).thenReturn("encoded_provided");
            when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(inv -> {
                Utilisateur u = inv.getArgument(0);
                u.setId(13L);
                return u;
            });
            when(utilisateurMapper.toResponse(any(Utilisateur.class))).thenReturn(dummyResponse);

            userService.createUser(req);

            ArgumentCaptor<Utilisateur> captor = ArgumentCaptor.forClass(Utilisateur.class);
            verify(utilisateurRepository).save(captor.capture());
            assertThat(captor.getValue().getMotDePasse()).isEqualTo("encoded_provided");
            // generateRandomPassword is false → no email sent
            verify(notificationService, never()).sendEmail(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("generates random password when motDePasse is null even if generateRandomPassword is false")
        void createUser_NullPassword_ForcesRandom() {
            CreateUserRequest req = baseRequest()
                    .role(RoleUtilisateur.ELECTEUR)
                    .motDePasse(null)
                    .generateRandomPassword(false)
                    .build();

            when(utilisateurRepository.existsByEmail(anyString())).thenReturn(false);
            when(utilisateurRepository.existsByCin(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("encoded_random");
            when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(inv -> {
                Utilisateur u = inv.getArgument(0);
                u.setId(14L);
                return u;
            });
            when(utilisateurMapper.toResponse(any(Utilisateur.class))).thenReturn(dummyResponse);

            userService.createUser(req);

            verify(passwordEncoder).encode(argThat(pwd -> pwd != null && pwd.length() == 12));
        }

        @Test
        @DisplayName("throws DuplicateResourceException when email is taken")
        void createUser_DuplicateEmail() {
            CreateUserRequest req = baseRequest().role(RoleUtilisateur.ELECTEUR).build();
            when(utilisateurRepository.existsByEmail(anyString())).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(req))
                    .isInstanceOf(DuplicateResourceException.class);

            verify(utilisateurRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws DuplicateResourceException when CIN is taken")
        void createUser_DuplicateCin() {
            CreateUserRequest req = baseRequest().role(RoleUtilisateur.ELECTEUR).build();
            when(utilisateurRepository.existsByEmail(anyString())).thenReturn(false);
            when(utilisateurRepository.existsByCin(anyString())).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(req))
                    .isInstanceOf(DuplicateResourceException.class);

            verify(utilisateurRepository, never()).save(any());
        }
    }

    // ================================================================
    // populateBase — covered indirectly through createUser
    // Verify fields are trimmed/uppercased/lowercased
    // ================================================================

    @Test
    @DisplayName("populateBase trims and normalizes CIN, nom, prenom, email")
    void populateBase_NormalizesFields() {
        CreateUserRequest req = baseRequest()
                .cin("  ab1234  ")
                .nom("  Durand  ")
                .prenom("  Marie  ")
                .email("  Marie@Example.COM  ")
                .role(RoleUtilisateur.ELECTEUR)
                .generateRandomPassword(false)
                .motDePasse("pass")
                .build();

        when(utilisateurRepository.existsByEmail(anyString())).thenReturn(false);
        when(utilisateurRepository.existsByCin(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("enc");
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));
        when(utilisateurMapper.toResponse(any(Utilisateur.class))).thenReturn(dummyResponse);

        userService.createUser(req);

        ArgumentCaptor<Utilisateur> captor = ArgumentCaptor.forClass(Utilisateur.class);
        verify(utilisateurRepository).save(captor.capture());
        Utilisateur saved = captor.getValue();
        assertThat(saved.getCin()).isEqualTo("AB1234");
        assertThat(saved.getNom()).isEqualTo("Durand");
        assertThat(saved.getPrenom()).isEqualTo("Marie");
        assertThat(saved.getEmail()).isEqualTo("marie@example.com");
        assertThat(saved.isEnabled()).isTrue();
    }

    // ================================================================
    // Read operations
    // ================================================================

    @Nested
    @DisplayName("Read operations")
    class ReadOperations {

        @Test
        @DisplayName("findAll returns all users mapped to responses")
        void findAll_List() {
            when(utilisateurRepository.findAll()).thenReturn(List.of(electeur, admin));
            when(utilisateurMapper.toResponse(any(Utilisateur.class))).thenReturn(dummyResponse);

            List<UserResponse> result = userService.findAll();

            assertThat(result).hasSize(2);
            verify(utilisateurMapper, times(2)).toResponse(any(Utilisateur.class));
        }

        @Test
        @DisplayName("findAll pageable returns paged results")
        void findAll_Pageable() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Utilisateur> page = new PageImpl<>(List.of(electeur), pageable, 1);
            when(utilisateurRepository.findAll(pageable)).thenReturn(page);
            when(utilisateurMapper.toResponse(any(Utilisateur.class))).thenReturn(dummyResponse);

            Page<UserResponse> result = userService.findAll(pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("findAllByRole returns filtered users")
        void findAllByRole() {
            when(utilisateurRepository.findAllByRole(RoleUtilisateur.ADMIN)).thenReturn(List.of(admin));
            when(utilisateurMapper.toResponse(any(Utilisateur.class))).thenReturn(dummyResponse);

            List<UserResponse> result = userService.findAllByRole(RoleUtilisateur.ADMIN);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("findAllActive returns only enabled users")
        void findAllActive() {
            when(utilisateurRepository.findAllByIsEnabledTrue()).thenReturn(List.of(electeur));
            when(utilisateurMapper.toResponse(any(Utilisateur.class))).thenReturn(dummyResponse);

            List<UserResponse> result = userService.findAllActive();

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("findById returns user when found")
        void findById_Success() {
            when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(electeur));
            when(utilisateurMapper.toResponse(any(Utilisateur.class))).thenReturn(dummyResponse);

            UserResponse result = userService.findById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("findById throws ResourceNotFoundException when not found")
        void findById_NotFound() {
            when(utilisateurRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.findById(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("findByEmail returns user when found")
        void findByEmail_Success() {
            when(utilisateurRepository.findByEmail("jean@example.com"))
                    .thenReturn(Optional.of(electeur));
            when(utilisateurMapper.toResponse(any(Utilisateur.class))).thenReturn(dummyResponse);

            UserResponse result = userService.findByEmail("jean@example.com");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("findByEmail throws ResourceNotFoundException when not found")
        void findByEmail_NotFound() {
            when(utilisateurRepository.findByEmail("unknown@example.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.findByEmail("unknown@example.com"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("getCurrentUserProfile returns profile of authenticated user")
        void getCurrentUserProfile() {
            // Mock SecurityContextHolder
            Authentication auth = mock(Authentication.class);
            SecurityContext secCtx = mock(SecurityContext.class);
            when(secCtx.getAuthentication()).thenReturn(auth);
            when(auth.getName()).thenReturn("jean@example.com");
            SecurityContextHolder.setContext(secCtx);

            when(utilisateurRepository.findByEmail("jean@example.com"))
                    .thenReturn(Optional.of(electeur));
            when(utilisateurMapper.toResponse(any(Utilisateur.class))).thenReturn(dummyResponse);

            UserResponse result = userService.getCurrentUserProfile();

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("jean@example.com");

            // Clean up
            SecurityContextHolder.clearContext();
        }
    }

    // ================================================================
    // Search
    // ================================================================

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("search with keyword returns matching users")
        void search_WithKeyword() {
            when(utilisateurRepository.searchByKeyword("dup"))
                    .thenReturn(List.of(electeur));
            when(utilisateurMapper.toResponse(any(Utilisateur.class))).thenReturn(dummyResponse);

            List<UserResponse> result = userService.search("  dup  ");

            assertThat(result).hasSize(1);
            verify(utilisateurRepository).searchByKeyword("dup");
        }

        @Test
        @DisplayName("search with null keyword returns all users")
        void search_NullKeyword() {
            when(utilisateurRepository.findAll()).thenReturn(List.of(electeur));
            when(utilisateurMapper.toResponse(any(Utilisateur.class))).thenReturn(dummyResponse);

            List<UserResponse> result = userService.search(null);

            assertThat(result).hasSize(1);
            verify(utilisateurRepository).findAll();
            verify(utilisateurRepository, never()).searchByKeyword(anyString());
        }

        @Test
        @DisplayName("search with blank keyword returns all users")
        void search_BlankKeyword() {
            when(utilisateurRepository.findAll()).thenReturn(List.of(electeur));
            when(utilisateurMapper.toResponse(any(Utilisateur.class))).thenReturn(dummyResponse);

            List<UserResponse> result = userService.search("   ");

            assertThat(result).hasSize(1);
            verify(utilisateurRepository).findAll();
        }
    }

    // ================================================================
    // Write operations
    // ================================================================

    @Nested
    @DisplayName("updateRole")
    class UpdateRole {

        @Test
        @DisplayName("updates role successfully")
        void updateRole_Success() {
            when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(electeur));
            when(utilisateurMapper.toResponse(any(Utilisateur.class))).thenReturn(dummyResponse);

            UserResponse result = userService.updateRole(1L, RoleUtilisateur.ADMIN);

            assertThat(result).isNotNull();
            verify(utilisateurRepository).updateRole(1L, RoleUtilisateur.ADMIN);
        }

        @Test
        @DisplayName("returns unchanged when role is the same")
        void updateRole_SameRole() {
            when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(electeur));
            when(utilisateurMapper.toResponse(any(Utilisateur.class))).thenReturn(dummyResponse);

            UserResponse result = userService.updateRole(1L, RoleUtilisateur.ELECTEUR);

            assertThat(result).isNotNull();
            verify(utilisateurRepository, never()).updateRole(anyLong(), any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when user not found")
        void updateRole_NotFound() {
            when(utilisateurRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateRole(999L, RoleUtilisateur.ADMIN))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("setAccountEnabled")
    class SetAccountEnabled {

        @Test
        @DisplayName("activates user account")
        void setAccountEnabled_Activate() {
            electeur.setEnabled(false);
            when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(electeur));
            when(utilisateurMapper.toResponse(any(Utilisateur.class))).thenReturn(dummyResponse);

            UserResponse result = userService.setAccountEnabled(1L, true);

            assertThat(result).isNotNull();
            verify(utilisateurRepository).updateEnabledStatus(1L, true);
        }

        @Test
        @DisplayName("deactivates user account")
        void setAccountEnabled_Deactivate() {
            electeur.setEnabled(true);
            when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(electeur));
            when(utilisateurMapper.toResponse(any(Utilisateur.class))).thenReturn(dummyResponse);

            UserResponse result = userService.setAccountEnabled(1L, false);

            assertThat(result).isNotNull();
            verify(utilisateurRepository).updateEnabledStatus(1L, false);
        }

        @Test
        @DisplayName("returns unchanged when status is already the same (enabled=true)")
        void setAccountEnabled_AlreadyEnabled() {
            electeur.setEnabled(true);
            when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(electeur));
            when(utilisateurMapper.toResponse(any(Utilisateur.class))).thenReturn(dummyResponse);

            UserResponse result = userService.setAccountEnabled(1L, true);

            assertThat(result).isNotNull();
            verify(utilisateurRepository, never()).updateEnabledStatus(anyLong(), anyBoolean());
        }

        @Test
        @DisplayName("returns unchanged when status is already the same (enabled=false)")
        void setAccountEnabled_AlreadyDisabled() {
            electeur.setEnabled(false);
            when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(electeur));
            when(utilisateurMapper.toResponse(any(Utilisateur.class))).thenReturn(dummyResponse);

            UserResponse result = userService.setAccountEnabled(1L, false);

            assertThat(result).isNotNull();
            verify(utilisateurRepository, never()).updateEnabledStatus(anyLong(), anyBoolean());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when user not found")
        void setAccountEnabled_NotFound() {
            when(utilisateurRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.setAccountEnabled(999L, true))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deactivateUser")
    class DeactivateUser {

        @Test
        @DisplayName("deactivates the user by delegating to setAccountEnabled(id, false)")
        void deactivateUser_DelegatesToSetAccountEnabled() {
            electeur.setEnabled(true);
            when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(electeur));
            when(utilisateurMapper.toResponse(any(Utilisateur.class))).thenReturn(dummyResponse);

            userService.deactivateUser(1L);

            verify(utilisateurRepository).updateEnabledStatus(1L, false);
        }
    }

    // ================================================================
    // Statistics
    // ================================================================

    @Nested
    @DisplayName("Statistics")
    class Statistics {

        @Test
        @DisplayName("countAll returns total user count")
        void countAll() {
            when(utilisateurRepository.count()).thenReturn(42L);

            assertThat(userService.countAll()).isEqualTo(42L);
        }

        @Test
        @DisplayName("countByRole returns role-specific count")
        void countByRole() {
            when(utilisateurRepository.countByRole(RoleUtilisateur.ELECTEUR)).thenReturn(15L);

            assertThat(userService.countByRole(RoleUtilisateur.ELECTEUR)).isEqualTo(15L);
        }

        @Test
        @DisplayName("countActive returns number of enabled users")
        void countActive() {
            when(utilisateurRepository.findAllByIsEnabledTrue()).thenReturn(List.of(electeur, admin));

            assertThat(userService.countActive()).isEqualTo(2L);
        }

        @Test
        @DisplayName("countActive returns 0 when no enabled users")
        void countActive_Empty() {
            when(utilisateurRepository.findAllByIsEnabledTrue()).thenReturn(Collections.emptyList());

            assertThat(userService.countActive()).isEqualTo(0L);
        }
    }

    // ================================================================
    // Internal helpers
    // ================================================================

    @Nested
    @DisplayName("Internal helpers")
    class InternalHelpers {

        @Test
        @DisplayName("findEntityById returns entity when found")
        void findEntityById_Success() {
            when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(electeur));

            Utilisateur result = userService.findEntityById(1L);

            assertThat(result).isSameAs(electeur);
        }

        @Test
        @DisplayName("findEntityById throws when not found")
        void findEntityById_NotFound() {
            when(utilisateurRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.findEntityById(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("toResponse delegates to utilisateurMapper")
        void toResponse_DelegatesToMapper() {
            when(utilisateurMapper.toResponse(any(Utilisateur.class))).thenReturn(dummyResponse);

            UserResponse result = userService.toResponse(electeur);

            assertThat(result).isSameAs(dummyResponse);
        }

        @Test
        @DisplayName("assertEmailNotTaken does nothing when email is available")
        void assertEmailNotTaken_OK() {
            when(utilisateurRepository.existsByEmail("new@test.com")).thenReturn(false);

            assertThatCode(() -> userService.assertEmailNotTaken("new@test.com"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("assertEmailNotTaken throws when email exists")
        void assertEmailNotTaken_Duplicate() {
            when(utilisateurRepository.existsByEmail("taken@test.com")).thenReturn(true);

            assertThatThrownBy(() -> userService.assertEmailNotTaken("taken@test.com"))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("taken@test.com");
        }

        @Test
        @DisplayName("assertCinNotTaken does nothing when CIN is available")
        void assertCinNotTaken_OK() {
            when(utilisateurRepository.existsByCin("NEW123")).thenReturn(false);

            assertThatCode(() -> userService.assertCinNotTaken("NEW123"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("assertCinNotTaken throws when CIN exists")
        void assertCinNotTaken_Duplicate() {
            when(utilisateurRepository.existsByCin("TAKEN1")).thenReturn(true);

            assertThatThrownBy(() -> userService.assertCinNotTaken("TAKEN1"))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("TAKEN1");
        }
    }
}
