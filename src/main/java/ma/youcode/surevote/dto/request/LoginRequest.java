package ma.youcode.surevote.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for user authentication (login).
 * Used by POST /api/auth/login
 */
@Schema(description = "Payload d'authentification utilisateur")
public record LoginRequest(

        @Schema(description = "Adresse email du compte", example = "voter@example.com")
        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "Format d'email invalide")
        String email,

        @Schema(description = "Mot de passe utilisateur", example = "Secret123!", minLength = 6)
        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = 6, max = 100, message = "Le mot de passe doit contenir entre 6 et 100 caractères")
        String motDePasse
) {}
