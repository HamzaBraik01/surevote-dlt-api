package ma.youcode.surevote.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for password reset request endpoint.
 * Confirms that a reset email has been sent.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Confirmation that password reset email has been sent")
public class PasswordResetResponse {

    @Schema(description = "Whether the reset email was successfully sent", example = "true")
    private boolean success;

    @Schema(description = "Human-readable confirmation message",
            example = "Un email de réinitialisation a été envoyé à votre adresse email. Le lien expire dans 15 minutes.")
    private String message;

    @Schema(description = "The masked email address for confirmation", example = "u***@example.com")
    private String email;
}
