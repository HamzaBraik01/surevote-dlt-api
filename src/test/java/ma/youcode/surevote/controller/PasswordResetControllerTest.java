package ma.youcode.surevote.controller;

import ma.youcode.surevote.dto.request.PasswordResetConfirmRequest;
import ma.youcode.surevote.dto.request.PasswordResetRequest;
import ma.youcode.surevote.dto.response.PasswordResetResponse;
import ma.youcode.surevote.service.PasswordResetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetControllerTest {

    @Mock private PasswordResetService passwordResetService;
    @InjectMocks private PasswordResetController controller;

    @Test
    void requestReset_shouldReturn200() {
        PasswordResetRequest req = PasswordResetRequest.builder().email("user@example.com").build();
        PasswordResetResponse resp = new PasswordResetResponse();
        when(passwordResetService.requestPasswordReset("user@example.com")).thenReturn(resp);

        ResponseEntity<PasswordResetResponse> response = controller.requestReset(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(resp);
        verify(passwordResetService).requestPasswordReset("user@example.com");
    }

    @Test
    void confirmReset_shouldReturn200() {
        PasswordResetConfirmRequest req = PasswordResetConfirmRequest.builder()
                .token("token-123")
                .newPassword("NewPass123!")
                .confirmPassword("NewPass123!")
                .build();

        ResponseEntity<String> response = controller.confirmReset(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("réinitialisé");
        verify(passwordResetService).resetPassword("token-123", "NewPass123!", "NewPass123!");
    }
}
