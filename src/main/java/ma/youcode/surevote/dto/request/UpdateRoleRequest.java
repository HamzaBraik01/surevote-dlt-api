package ma.youcode.surevote.dto.request;

import jakarta.validation.constraints.NotNull;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;

public record UpdateRoleRequest(
    @NotNull(message = "Le rôle est obligatoire")
    RoleUtilisateur role
) {}
