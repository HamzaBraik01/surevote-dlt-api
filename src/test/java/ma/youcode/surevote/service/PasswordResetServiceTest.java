package ma.youcode.surevote.service;

import ma.youcode.surevote.domain.entity.PasswordResetToken;
import ma.youcode.surevote.domain.entity.Utilisateur;
import ma.youcode.surevote.domain.entity.Electeur;
import ma.youcode.surevote.dto.response.PasswordResetResponse;
import ma.youcode.surevote.exception.InvalidOtpException;
import ma.youcode.surevote.exception.ResourceNotFoundException;
import ma.youcode.surevote.repository.PasswordResetTokenRepository;
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
@DisplayName("PasswordResetService")
class PasswordResetServiceTest {

    @Mock private PasswordResetTokenRepository resetTokenRepo;
    @Mock private UtilisateurRepository utilisateurRepo;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private NotificationService notificationService;
    @InjectMocks private PasswordResetService service;

    private Electeur user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "resetTokenExpiryMinutes", 15);
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:4200");

        user = new Electeur();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setPrenom("John");
        user.setNom("Doe");
    }

    // ── requestPasswordReset ───────────────────────────────────

    @Test @DisplayName("requestPasswordReset generates token, saves, and sends email")
    void request_success() {
        when(utilisateurRepo.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(resetTokenRepo.countValidTokensByUserId(eq(1L), any())).thenReturn(0L);
        when(resetTokenRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PasswordResetResponse result = service.requestPasswordReset("test@example.com");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getEmail()).contains("***"); // masked
        verify(notificationService).sendEmail(eq("test@example.com"), anyString(), anyString());
        verify(resetTokenRepo).save(any(PasswordResetToken.class));
    }

    @Test @DisplayName("requestPasswordReset still proceeds when rate limited (silent)")
    void request_rateLimited() {
        when(utilisateurRepo.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(resetTokenRepo.countValidTokensByUserId(eq(1L), any())).thenReturn(5L);
        when(resetTokenRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PasswordResetResponse result = service.requestPasswordReset("test@example.com");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test @DisplayName("requestPasswordReset throws when user not found")
    void request_userNotFound() {
        when(utilisateurRepo.findByEmail("unknown@test.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requestPasswordReset("unknown@test.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test @DisplayName("requestPasswordReset propagates mail exception")
    void request_mailFails() {
        when(utilisateurRepo.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(resetTokenRepo.countValidTokensByUserId(eq(1L), any())).thenReturn(0L);
        when(resetTokenRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new org.springframework.mail.MailSendException("SMTP error"))
                .when(notificationService).sendEmail(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.requestPasswordReset("test@example.com"))
                .isInstanceOf(org.springframework.mail.MailSendException.class);
    }

    // ── verifyResetToken ───────────────────────────────────────

    @Test @DisplayName("verifyResetToken returns valid token")
    void verify_success() {
        PasswordResetToken token = PasswordResetToken.builder()
                .token("hashed").utilisateur(user).used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(10)).build();

        when(resetTokenRepo.findByToken(anyString())).thenReturn(Optional.of(token));

        PasswordResetToken result = service.verifyResetToken("raw-uuid");
        assertThat(result).isEqualTo(token);
    }

    @Test @DisplayName("verifyResetToken throws when token not found")
    void verify_tokenNotFound() {
        when(resetTokenRepo.findByToken(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verifyResetToken("bad-token"))
                .isInstanceOf(InvalidOtpException.class);
    }

    @Test @DisplayName("verifyResetToken throws when token is invalid (expired or used)")
    void verify_tokenInvalid() {
        PasswordResetToken token = PasswordResetToken.builder()
                .token("hashed").utilisateur(user).used(true)
                .expiresAt(LocalDateTime.now().minusMinutes(1)).build();

        when(resetTokenRepo.findByToken(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.verifyResetToken("some-token"))
                .isInstanceOf(InvalidOtpException.class);
    }

    // ── resetPassword ──────────────────────────────────────────

    @Test @DisplayName("resetPassword updates password and marks token used")
    void reset_success() {
        PasswordResetToken token = PasswordResetToken.builder()
                .token("hashed").utilisateur(user).used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(10)).build();

        when(resetTokenRepo.findByToken(anyString())).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("newPass")).thenReturn("$2a$new");
        when(utilisateurRepo.save(any())).thenReturn(user);
        when(resetTokenRepo.save(any())).thenReturn(token);

        service.resetPassword("raw-token", "newPass", "newPass");

        assertThat(user.getMotDePasse()).isEqualTo("$2a$new");
        assertThat(token.isUsed()).isTrue();
        assertThat(token.getUsedAt()).isNotNull();
    }

    @Test @DisplayName("resetPassword throws when passwords don't match")
    void reset_passwordMismatch() {
        PasswordResetToken token = PasswordResetToken.builder()
                .token("hashed").utilisateur(user).used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(10)).build();

        when(resetTokenRepo.findByToken(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword("raw-token", "pass1", "pass2"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── maskEmail (tested via requestPasswordReset response) ───

    @Test @DisplayName("maskEmail handles null email")
    void request_masksEmail_null() {
        // Test via reflection for the private maskEmail method
        String result = (String) ReflectionTestUtils.invokeMethod(service, "maskEmail", (String) null);
        assertThat(result).isNull();
    }

    @Test @DisplayName("maskEmail handles empty email")
    void request_masksEmail_empty() {
        String result = (String) ReflectionTestUtils.invokeMethod(service, "maskEmail", "");
        assertThat(result).isEmpty();
    }

    @Test @DisplayName("maskEmail handles malformed email without @")
    void request_masksEmail_noAt() {
        String result = (String) ReflectionTestUtils.invokeMethod(service, "maskEmail", "noatsign");
        assertThat(result).isEqualTo("noatsign");
    }

    @Test @DisplayName("maskEmail masks normal email")
    void request_masksEmail_normal() {
        String result = (String) ReflectionTestUtils.invokeMethod(service, "maskEmail", "user@example.com");
        assertThat(result).isEqualTo("u***@example.com");
    }

    // ── hashToken (tested indirectly but verify via reflection) ─

    @Test @DisplayName("hashToken produces hex SHA-256 string")
    void hashToken_producesHash() {
        String hash = (String) ReflectionTestUtils.invokeMethod(service, "hashToken", "test-token");
        assertThat(hash).hasSize(64); // SHA-256 hex = 64 chars
        assertThat(hash).matches("[0-9a-f]+");
    }
}
