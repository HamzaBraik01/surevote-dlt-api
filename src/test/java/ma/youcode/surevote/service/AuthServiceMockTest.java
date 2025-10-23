package ma.youcode.surevote.service;

import ma.youcode.surevote.domain.entity.Administrateur;
import ma.youcode.surevote.domain.entity.Electeur;
import ma.youcode.surevote.domain.entity.Observateur;
import ma.youcode.surevote.domain.entity.Utilisateur;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import ma.youcode.surevote.dto.request.LoginRequest;
import ma.youcode.surevote.dto.request.OtpVerificationRequest;
import ma.youcode.surevote.dto.request.RegisterRequest;
import ma.youcode.surevote.dto.response.AuthResponse;
import ma.youcode.surevote.exception.DuplicateResourceException;
import ma.youcode.surevote.exception.JwtAuthenticationException;
import ma.youcode.surevote.exception.ResourceNotFoundException;
import ma.youcode.surevote.repository.UtilisateurRepository;
import ma.youcode.surevote.security.JwtTokenProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService (Mockito)")
class AuthServiceMockTest {

    @Mock private UtilisateurRepository utilisateurRepo;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private OtpService otpService;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Spy  private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @InjectMocks private AuthService authService;

    private RegisterRequest validRegister;
    private Electeur testElecteur;

    @BeforeEach
    void setUp() {
        validRegister = RegisterRequest.builder()
                .cin("AB123456")
                .nom("Dupont")
                .prenom("Jean")
                .email("jean@example.com")
                .motDePasse("SecurePass@123")
                .confirmationMotDePasse("SecurePass@123")
                .role(RoleUtilisateur.ELECTEUR)
                .build();

        testElecteur = new Electeur();
        testElecteur.setId(1L);
        testElecteur.setEmail("jean@example.com");
        testElecteur.setNom("Dupont");
        testElecteur.setPrenom("Jean");
        testElecteur.setRole(RoleUtilisateur.ELECTEUR);
        testElecteur.setEnabled(true);
        testElecteur.setDoubleFacteurActif(false); // Electeur defaults to true
    }

    // ── Registration ─────────────────────────────────────

    @Test @DisplayName("register success — ELECTEUR")
    void register_success() {
        when(utilisateurRepo.existsByEmail(anyString())).thenReturn(false);
        when(utilisateurRepo.existsByCin(anyString())).thenReturn(false);
        when(utilisateurRepo.save(any(Utilisateur.class))).thenReturn(testElecteur);
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("at");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("rt");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(3600000L);

        AuthResponse resp = authService.register(validRegister);
        assertThat(resp).isNotNull();
        assertThat(resp.getAccessToken()).isEqualTo("at");
        verify(utilisateurRepo).save(any(Electeur.class));
    }

    @Test @DisplayName("register — duplicate email throws DuplicateResourceException")
    void register_duplicateEmail() {
        when(utilisateurRepo.existsByEmail(anyString())).thenReturn(true);
        assertThatThrownBy(() -> authService.register(validRegister))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test @DisplayName("register — duplicate CIN throws DuplicateResourceException")
    void register_duplicateCin() {
        when(utilisateurRepo.existsByEmail(anyString())).thenReturn(false);
        when(utilisateurRepo.existsByCin(anyString())).thenReturn(true);
        assertThatThrownBy(() -> authService.register(validRegister))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test @DisplayName("register — password mismatch throws IllegalArgumentException")
    void register_passwordMismatch() {
        when(utilisateurRepo.existsByEmail(anyString())).thenReturn(false);
        when(utilisateurRepo.existsByCin(anyString())).thenReturn(false);
        validRegister.setConfirmationMotDePasse("Different@123");
        assertThatThrownBy(() -> authService.register(validRegister))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Login ────────────────────────────────────────────

    @Test @DisplayName("login success — no 2FA")
    void login_success() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(testElecteur);
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("at");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("rt");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(3600000L);

        AuthResponse resp = authService.login(new LoginRequest("jean@example.com", "pass"));
        assertThat(resp.getAccessToken()).isEqualTo("at");
        verify(otpService, never()).generateAndSendOtp(any());
    }

    @Test @DisplayName("login success — 2FA triggers OTP")
    void login_with2FA() {
        testElecteur.setDoubleFacteurActif(true);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(testElecteur);
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("at");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("rt");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(3600000L);

        AuthResponse resp = authService.login(new LoginRequest("jean@example.com", "pass"));
        assertThat(resp).isNotNull();
        verify(otpService).generateAndSendOtp(testElecteur);
        verify(utilisateurRepo).save(testElecteur);
    }

    @Test @DisplayName("login — bad credentials throws")
    void login_badCredentials() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad"));
        assertThatThrownBy(() -> authService.login(new LoginRequest("x", "y")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test @DisplayName("login — non-Electeur user returns standard response")
    void login_adminUser() {
        Administrateur admin = new Administrateur();
        admin.setId(2L);
        admin.setEmail("admin@test.com");
        admin.setNom("Admin");
        admin.setPrenom("Test");
        admin.setRole(RoleUtilisateur.ADMIN);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(admin);
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("at");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("rt");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(3600000L);

        AuthResponse resp = authService.login(new LoginRequest("admin@test.com", "pass"));
        assertThat(resp.getRole()).isEqualTo(RoleUtilisateur.ADMIN);
    }

    // ── Token Refresh ────────────────────────────────────

    @Test @DisplayName("refreshToken success")
    void refreshToken_success() {
        when(jwtTokenProvider.validateToken("rt")).thenReturn(true);
        when(jwtTokenProvider.getEmailFromToken("rt")).thenReturn("jean@example.com");
        when(utilisateurRepo.findByEmail("jean@example.com")).thenReturn(Optional.of(testElecteur));
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("newAt");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("newRt");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(3600000L);

        AuthResponse resp = authService.refreshToken("rt");
        assertThat(resp.getAccessToken()).isEqualTo("newAt");
    }

    @Test @DisplayName("refreshToken — invalid token throws JwtAuthenticationException")
    void refreshToken_invalidToken() {
        when(jwtTokenProvider.validateToken("bad")).thenReturn(false);
        assertThatThrownBy(() -> authService.refreshToken("bad"))
                .isInstanceOf(JwtAuthenticationException.class);
    }

    @Test @DisplayName("refreshToken — user not found throws ResourceNotFoundException")
    void refreshToken_userNotFound() {
        when(jwtTokenProvider.validateToken("rt")).thenReturn(true);
        when(jwtTokenProvider.getEmailFromToken("rt")).thenReturn("ghost@test.com");
        when(utilisateurRepo.findByEmail("ghost@test.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.refreshToken("rt"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test @DisplayName("refreshToken — disabled user throws JwtAuthenticationException")
    void refreshToken_disabledUser() {
        testElecteur.setEnabled(false);
        when(jwtTokenProvider.validateToken("rt")).thenReturn(true);
        when(jwtTokenProvider.getEmailFromToken("rt")).thenReturn("jean@example.com");
        when(utilisateurRepo.findByEmail("jean@example.com")).thenReturn(Optional.of(testElecteur));
        assertThatThrownBy(() -> authService.refreshToken("rt"))
                .isInstanceOf(JwtAuthenticationException.class);
    }

    // ── Logout ───────────────────────────────────────────

    @Test @DisplayName("logout success")
    void logout_success() {
        when(jwtTokenProvider.validateToken("token")).thenReturn(true);
        when(jwtTokenProvider.getExpirationDateFromToken("token"))
                .thenReturn(Date.from(Instant.now().plusSeconds(3600)));
        authService.logout("token");
        verify(tokenBlacklistService).blacklist(eq("token"), any(Instant.class));
    }

    @Test @DisplayName("logout — null token throws")
    void logout_nullToken() {
        assertThatThrownBy(() -> authService.logout(null))
                .isInstanceOf(JwtAuthenticationException.class);
    }

    @Test @DisplayName("logout — blank token throws")
    void logout_blankToken() {
        assertThatThrownBy(() -> authService.logout("   "))
                .isInstanceOf(JwtAuthenticationException.class);
    }

    @Test @DisplayName("logout — invalid token throws")
    void logout_invalidToken() {
        when(jwtTokenProvider.validateToken("bad")).thenReturn(false);
        assertThatThrownBy(() -> authService.logout("bad"))
                .isInstanceOf(JwtAuthenticationException.class);
    }

    // ── verifyOtp ────────────────────────────────────────

    @Test @DisplayName("verifyOtp success")
    void verifyOtp_success() {
        OtpVerificationRequest req = new OtpVerificationRequest("123456");
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("at");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("rt");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(3600000L);

        AuthResponse resp = authService.verifyOtp(req, testElecteur);
        verify(otpService).verifyOtp(testElecteur, "123456");
        assertThat(resp.getAccessToken()).isEqualTo("at");
    }

    // ── resendOtp ────────────────────────────────────────

    @Test @DisplayName("resendOtp calls otpService")
    void resendOtp_success() {
        authService.resendOtp(testElecteur);
        verify(otpService).generateAndSendOtp(testElecteur);
    }

    // ── buildAuthResponse — non-Electeur path ────────────

    @Test @DisplayName("refreshToken — non-Electeur returns AuthResponse.of()")
    void refreshToken_nonElecteur() {
        Observateur obs = new Observateur();
        obs.setId(3L);
        obs.setEmail("obs@test.com");
        obs.setNom("Obs");
        obs.setPrenom("Test");
        obs.setRole(RoleUtilisateur.OBSERVATEUR);
        obs.setEnabled(true);

        when(jwtTokenProvider.validateToken("rt")).thenReturn(true);
        when(jwtTokenProvider.getEmailFromToken("rt")).thenReturn("obs@test.com");
        when(utilisateurRepo.findByEmail("obs@test.com")).thenReturn(Optional.of(obs));
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("at");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("rt");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(3600000L);

        AuthResponse resp = authService.refreshToken("rt");
        assertThat(resp.getRole()).isEqualTo(RoleUtilisateur.OBSERVATEUR);
    }
}
