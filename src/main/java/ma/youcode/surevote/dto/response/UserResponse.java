package ma.youcode.surevote.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;

import java.time.LocalDateTime;

/**
 * Response DTO for Utilisateur and its subtypes.
 * Exposes only safe, non-sensitive fields to the API consumer.
 * Never includes motDePasse, otpCode, or any credential field.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private Long id;
    private String cin;
    private String nom;
    private String prenom;
    private String email;
    private boolean isEnabled;
    private RoleUtilisateur role;

    // --- Electeur-specific fields (null for other roles) ---
    private String telephone;
    private Boolean doubleFacteurActif;

    // --- Administrateur-specific fields (null for other roles) ---
    private String departement;

    // --- Observateur-specific fields (null for other roles) ---
    private String organisme;

    // --- College info (for Electeur) ---
    private Long collegeElectoralId;
    private String collegeElectoralNom;

    // --- Metadata ---
    private LocalDateTime createdAt;
}
