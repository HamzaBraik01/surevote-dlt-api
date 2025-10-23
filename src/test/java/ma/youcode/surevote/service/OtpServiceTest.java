package ma.youcode.surevote.service;

import ma.youcode.surevote.domain.entity.Electeur;
import ma.youcode.surevote.exception.InvalidOtpException;
import ma.youcode.surevote.exception.ResourceNotFoundException;
import ma.youcode.surevote.repository.UtilisateurRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OtpService")
class OtpServiceTest {

    @Mock private NotificationService notificationService;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private OtpService otpService;

    private Electeur electeur;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(otpService, "otpExpiryMinutes", 5);
        ReflectionTestUtils.setField(otpService, "otpLength", 6);

        electeur = new Electeur();
        electeur.setId(1L);
        electeur.setNom("DOE");
        electeur.setPrenom("John");
        electeur.setEmail("john@test.com");
    }

    // ── generateAndSendOtp ─────────────────────────────────────

    @Test @DisplayName("generateAndSendOtp sends email and persists OTP")
    void generateAndSendOtp_success() {
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hash");
        when(utilisateurRepository.save(any())).thenReturn(electeur);

        otpService.generateAndSendOtp(electeur);

        verify(notificationService).sendEmail(eq("john@test.com"), anyString(), anyString());
        verify(utilisateurRepository).save(electeur);
    }

    @Test @DisplayName("generateAndSendOtp propagates mail exception and does not persist")
    void generateAndSendOtp_mailFails() {
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hash");
        doThrow(new RuntimeException("Mail error"))
                .when(notificationService).sendEmail(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> otpService.generateAndSendOtp(electeur))
                .isInstanceOf(RuntimeException.class);

        verify(utilisateurRepository, never()).save(any());
    }

    // ── verifyOtp ──────────────────────────────────────────────

    @Test @DisplayName("verifyOtp succeeds with correct code")
    void verifyOtp_success() {
        electeur.setOtpCode("$2a$hashedCode");
        electeur.setOtpExpiry(LocalDateTime.now().plusMinutes(5));
        electeur.setOtpVerified(false);

        when(passwordEncoder.matches("123456", "$2a$hashedCode")).thenReturn(true);
        when(utilisateurRepository.save(any())).thenReturn(electeur);

        otpService.verifyOtp(electeur, "123456");

        verify(utilisateurRepository).save(electeur);
    }

    @Test @DisplayName("verifyOtp throws when no OTP pending")
    void verifyOtp_noOtpPending() {
        electeur.setOtpCode(null);

        assertThatThrownBy(() -> otpService.verifyOtp(electeur, "123456"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("Aucun code OTP");
    }

    @Test @DisplayName("verifyOtp throws when OTP expired")
    void verifyOtp_expired() {
        electeur.setOtpCode("$2a$hash");
        electeur.setOtpExpiry(LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> otpService.verifyOtp(electeur, "123456"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("expiré");
    }

    @Test @DisplayName("verifyOtp throws when code does not match")
    void verifyOtp_wrongCode() {
        electeur.setOtpCode("$2a$hash");
        electeur.setOtpExpiry(LocalDateTime.now().plusMinutes(5));
        when(passwordEncoder.matches("wrong", "$2a$hash")).thenReturn(false);

        assertThatThrownBy(() -> otpService.verifyOtp(electeur, "wrong"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("incorrect");
    }

    // ── resendOtp ──────────────────────────────────────────────

    @Test @DisplayName("resendOtp looks up electeur and regenerates OTP")
    void resendOtp_success() {
        when(utilisateurRepository.findByEmail("john@test.com")).thenReturn(Optional.of(electeur));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hash");
        when(utilisateurRepository.save(any())).thenReturn(electeur);

        otpService.resendOtp("john@test.com");

        verify(notificationService).sendEmail(eq("john@test.com"), anyString(), anyString());
    }

    @Test @DisplayName("resendOtp throws when user not found")
    void resendOtp_userNotFound() {
        when(utilisateurRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> otpService.resendOtp("unknown@test.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── isOtpVerified ──────────────────────────────────────────

    @Test @DisplayName("isOtpVerified returns true when verified")
    void isOtpVerified_true() {
        electeur.setOtpVerified(true);
        assertThat(otpService.isOtpVerified(electeur)).isTrue();
    }

    @Test @DisplayName("isOtpVerified returns false when not verified")
    void isOtpVerified_false() {
        electeur.setOtpVerified(false);
        assertThat(otpService.isOtpVerified(electeur)).isFalse();
    }

    // ── invalidateOtp ──────────────────────────────────────────

    @Test @DisplayName("invalidateOtp clears OTP fields and saves")
    void invalidateOtp_withPendingOtp() {
        electeur.setOtpCode("$2a$hash");
        electeur.setOtpExpiry(LocalDateTime.now().plusMinutes(5));
        electeur.setOtpVerified(true);
        when(utilisateurRepository.save(any())).thenReturn(electeur);

        otpService.invalidateOtp(electeur);

        assertThat(electeur.getOtpCode()).isNull();
        assertThat(electeur.getOtpExpiry()).isNull();
        assertThat(electeur.isOtpVerified()).isFalse();
        verify(utilisateurRepository).save(electeur);
    }

    @Test @DisplayName("invalidateOtp no-op when no OTP pending")
    void invalidateOtp_noOtp() {
        electeur.setOtpCode(null);

        otpService.invalidateOtp(electeur);

        verify(utilisateurRepository, never()).save(any());
    }
}
