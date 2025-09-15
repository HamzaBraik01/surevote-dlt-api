package ma.youcode.surevote.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;

/**
 * Request DTO for new user registration.
 * Validated before processing to enforce data integrity at the API boundary.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

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

    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 8, max = 100, message = "Le mot de passe doit contenir entre 8 et 100 caractères")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
        message = "Le mot de passe doit contenir au moins une majuscule, une minuscule, un chiffre et un caractère spécial"
    )
    private String motDePasse;

    @NotBlank(message = "La confirmation du mot de passe est obligatoire")
    private String confirmationMotDePasse;

    // --- Role (assigned by admin, defaults to ELECTEUR for self-registration) ---
    private RoleUtilisateur role;

    // --- Electeur-specific fields (optional) ---
    @Pattern(
        regexp = "^(\\+?[0-9]{8,15})?$",
        message = "Numéro de téléphone invalide"
    )
    private String telephone;

    private boolean doubleFacteurActif = true;

    // --- Administrateur-specific fields (optional) ---
    @Size(max = 200, message = "Le département ne peut pas dépasser 200 caractères")
    private String departement;

    // --- Observateur-specific fields (optional) ---
    @Size(max = 200, message = "L'organisme ne peut pas dépasser 200 caractères")
    private String organisme;
}
