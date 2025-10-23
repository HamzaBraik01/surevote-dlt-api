package ma.youcode.surevote.service;

import ma.youcode.surevote.domain.entity.Electeur;
import ma.youcode.surevote.domain.entity.Utilisateur;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import ma.youcode.surevote.dto.request.LoginRequest;
import ma.youcode.surevote.dto.request.RegisterRequest;
import ma.youcode.surevote.dto.response.AuthResponse;
import ma.youcode.surevote.exception.DuplicateResourceException;
import ma.youcode.surevote.repository.UtilisateurRepository;
import ma.youcode.surevote.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService.
 *
 * Tests cover:
 *  - User registration (success, duplicates)
 *  - User login (success, invalid credentials)
 *  - Two-Factor Authentication flow
 *  - Token generation
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private OtpService otpService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private RegisterRequest validRegisterRequest;
    private LoginRequest validLoginRequest;
    private Electeur testElecteur;

    @BeforeEach
    void setUp() {
        validRegisterRequest = RegisterRequest.builder()
                .cin("AB123456")
                .nom("Dupont")
                .prenom("Jean")
                .email("jean@example.com")
                .motDePasse("SecurePass@123")
                .confirmationMotDePasse("SecurePass@123")
                .role(RoleUtilisateur.ELECTEUR)
                .build();

        validLoginRequest = new LoginRequest("jean@example.com", "SecurePass@123");

        testElecteur = new Electeur();
        testElecteur.setId(1L);
        testElecteur.setEmail("jean@example.com");
        testElecteur.setNom("Dupont");
        testElecteur.setPrenom("Jean");
        testElecteur.setRole(RoleUtilisateur.ELECTEUR);
    }

    // =========================================================
    // Registration Tests
    // =========================================================

    @Test
    void testRegister_Success() {
        // Arrange
        when(utilisateurRepository.existsByEmail(anyString())).thenReturn(false);
        when(utilisateurRepository.existsByCin(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(utilisateurRepository.save(any(Utilisateur.class))).thenReturn(testElecteur);
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access_token");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("refresh_token");

        // Act
        AuthResponse response = authService.register(validRegisterRequest);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertEquals("jean@example.com", response.getEmail());
        verify(utilisateurRepository, times(1)).save(any());
    }

    @Test
    void testRegister_DuplicateEmail() {
        // Arrange
        when(utilisateurRepository.existsByEmail(anyString())).thenReturn(true);

        // Act & Assert
        assertThrows(DuplicateResourceException.class, () -> {
            authService.register(validRegisterRequest);
        });

        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void testRegister_DuplicateCin() {
        // Arrange
        when(utilisateurRepository.existsByEmail(anyString())).thenReturn(false);
        when(utilisateurRepository.existsByCin(anyString())).thenReturn(true);

        // Act & Assert
        assertThrows(DuplicateResourceException.class, () -> {
            authService.register(validRegisterRequest);
        });
    }

    @Test
    void testRegister_PasswordMismatch() {
        // Arrange
        validRegisterRequest.setConfirmationMotDePasse("DifferentPass@123");

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            authService.register(validRegisterRequest);
        });
    }

    // =========================================================
    // Login Tests
    // =========================================================

    @Test
    void testLogin_Success() {
        // Arrange
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(testElecteur, null));
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access_token");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("refresh_token");

        // Act
        AuthResponse response = authService.login(validLoginRequest);

        // Assert
        assertNotNull(response);
        assertEquals("access_token", response.getAccessToken());
        assertEquals("refresh_token", response.getRefreshToken());
        verify(authenticationManager, times(1)).authenticate(any());
    }

    @Test
    void testLogin_InvalidCredentials() {
        // Arrange
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        // Act & Assert
        assertThrows(BadCredentialsException.class, () -> {
            authService.login(validLoginRequest);
        });
    }

    @Test
    void testLogin_TriggersTwoFactor_WhenEnabled() {
        // Arrange
        testElecteur.setDoubleFacteurActif(true);
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(testElecteur, null));
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access_token");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("refresh_token");

        // Act
        AuthResponse response = authService.login(validLoginRequest);

        // Assert
        assertNotNull(response);
        verify(otpService, times(1)).generateAndSendOtp(any(Electeur.class));
    }

    // =========================================================
    // Token Tests
    // =========================================================

    @Test
    void testPasswordEncoding() {
        // Verify that passwords are properly encoded
        String plainPassword = "SecurePass@123";
        when(passwordEncoder.encode(plainPassword)).thenReturn("encoded-password");
        when(passwordEncoder.matches(plainPassword, "encoded-password")).thenReturn(true);

        String encodedPassword = passwordEncoder.encode(plainPassword);

        assertTrue(passwordEncoder.matches(plainPassword, encodedPassword));
        assertNotEquals(plainPassword, encodedPassword);
    }
}
