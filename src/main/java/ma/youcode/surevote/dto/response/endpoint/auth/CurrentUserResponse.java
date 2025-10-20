package ma.youcode.surevote.dto.response.endpoint.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;

/**
 * DTO dédié à l'endpoint GET /api/auth/me.
 * Expose uniquement un snapshot de session (aucun champ sensible).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrentUserResponse {
    private Long userId;
    private String email;
    private String nom;
    private String prenom;
    private RoleUtilisateur role;
    private boolean enabled;

    // ELECTEUR-only
    private String telephone;
    private Boolean doubleFacteurActif;
    private Boolean otpVerified;
    private Long collegeElectoralId;
    private String collegeElectoralNom;

    // ADMIN / OBSERVATEUR optional
    private String departement;
    private String organisme;
}

