package ma.youcode.surevote.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for confirming a password reset with a valid token.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Payload de confirmation de réinitialisation du mot de passe")
public class PasswordResetConfirmRequest {

    @Schema(description = "Token reçu par email", example = "550e8400-e29b-41d4-a716-446655440000")
    @NotBlank(message = "Le token est obligatoire")
    private String token;

    @Schema(description = "Nouveau mot de passe", example = "NewPass123!", minLength = 8)
    @NotBlank(message = "Le nouveau mot de passe est obligatoire")
    @Size(min = 8, max = 100, message = "Le mot de passe doit contenir entre 8 et 100 caractères")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
        message = "Le mot de passe doit contenir au moins une majuscule, une minuscule, un chiffre et un caractère spécial"
    )
    private String newPassword;

    @Schema(description = "Confirmation du nouveau mot de passe", example = "NewPass123!")
    @NotBlank(message = "La confirmation du mot de passe est obligatoire")
    private String confirmPassword;
}
