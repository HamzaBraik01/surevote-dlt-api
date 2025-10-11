package ma.youcode.surevote.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;

/**
 * Request DTO for creating a new user (admin operation).
 * Allows creation of ADMIN, OBSERVATEUR, or ELECTEUR accounts.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUserRequest {

    @NotBlank(message = "Le CIN est obligatoire")
    @Size(min = 4, max = 20, message = "Le CIN doit contenir entre 4 et 20 caractères")
    @Pattern(regexp = "^[A-Z0-9]+$", message = "Le CIN ne doit contenir que des lettres majuscules et des chiffres")
    private String cin;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(min = 2, max = 100, message = "Le nom doit contenir entre 2 et 100 caractères")
    private String nom;

    @NotBlank(message = "Le prénom est obligatoire")
    @Size(min = 2, max = 100, message = "Le prénom doit contenir entre 2 et 100 caractères")
    private String prenom;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    @Size(max = 150, message = "L'email ne peut pas dépasser 150 caractères")
    private String email;

    @NotNull(message = "Le rôle est obligatoire")
    private RoleUtilisateur role;

    // --- Electeur-specific fields ---
    @Pattern(
        regexp = "^(\\+?[0-9]{8,15})?$",
        message = "Numéro de téléphone invalide"
    )
    private String telephone;

    private Boolean doubleFacteurActif = true;

    // --- Administrateur-specific fields ---
    @Size(max = 200, message = "Le département ne peut pas dépasser 200 caractères")
    private String departement;

    // --- Observateur-specific fields ---
    @Size(max = 200, message = "L'organisme ne peut pas dépasser 200 caractères")
    private String organisme;

    // --- Optional: Generate random password or use provided one ---
    private String motDePasse;
    
    private Boolean generateRandomPassword = true;
}
