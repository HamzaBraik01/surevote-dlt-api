package ma.youcode.surevote.mapper;

import ma.youcode.surevote.domain.entity.Administrateur;
import ma.youcode.surevote.domain.entity.Electeur;
import ma.youcode.surevote.domain.entity.Observateur;
import ma.youcode.surevote.domain.entity.Utilisateur;
import ma.youcode.surevote.dto.request.CreateUserRequest;
import ma.youcode.surevote.dto.request.RegisterRequest;
import ma.youcode.surevote.dto.response.UserResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Notes:
 * - Request -> Entity for users is intentionally partial. Password hashing, role forcing,
 *   and subtype instantiation remain in service/business logic.
 * - Entity -> Response is safe: never exposes credential fields (motDePasse, OTP code, etc.).
 */
@Mapper(config = MapStructConfig.class)
public interface UtilisateurMapper {

    // ---------------------------------------------------------------------
    // Entity -> Response (safe projection)
    // ---------------------------------------------------------------------

    @Mapping(target = "telephone", ignore = true)
    @Mapping(target = "doubleFacteurActif", ignore = true)
    @Mapping(target = "departement", ignore = true)
    @Mapping(target = "organisme", ignore = true)
    @Mapping(target = "collegeElectoralId", ignore = true)
    @Mapping(target = "collegeElectoralNom", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(source = "enabled", target = "isEnabled")
    UserResponse toBaseResponse(Utilisateur user);

    @Mapping(source = "collegeElectoral.id", target = "collegeElectoralId")
    @Mapping(source = "collegeElectoral.nom", target = "collegeElectoralNom")
    @Mapping(target = "departement", ignore = true)
    @Mapping(target = "organisme", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(source = "enabled", target = "isEnabled")
    UserResponse toResponse(Electeur electeur);

    @Mapping(target = "telephone", ignore = true)
    @Mapping(target = "doubleFacteurActif", ignore = true)
    @Mapping(target = "organisme", ignore = true)
    @Mapping(target = "collegeElectoralId", ignore = true)
    @Mapping(target = "collegeElectoralNom", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(source = "enabled", target = "isEnabled")
    UserResponse toResponse(Administrateur admin);

    @Mapping(target = "telephone", ignore = true)
    @Mapping(target = "doubleFacteurActif", ignore = true)
    @Mapping(target = "departement", ignore = true)
    @Mapping(target = "collegeElectoralId", ignore = true)
    @Mapping(target = "collegeElectoralNom", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(source = "enabled", target = "isEnabled")
    UserResponse toResponse(Observateur observateur);

    default UserResponse toResponse(Utilisateur user) {
        if (user == null) return null;
        if (user instanceof Electeur e) return toResponse(e);
        if (user instanceof Administrateur a) return toResponse(a);
        if (user instanceof Observateur o) return toResponse(o);
        return toBaseResponse(user);
    }

    // ---------------------------------------------------------------------
    // Request -> Entity (partial copy only; business logic stays in service)
    // ---------------------------------------------------------------------

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "motDePasse", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "collegeElectoral", ignore = true)
    @Mapping(target = "emargements", ignore = true)
    @Mapping(target = "otpCode", ignore = true)
    @Mapping(target = "otpExpiry", ignore = true)
    @Mapping(target = "otpVerified", ignore = true)
    @Mapping(target = "authorities", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateElecteurFromCreateUserRequest(CreateUserRequest request, @MappingTarget Electeur electeur);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "motDePasse", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "authorities", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateAdministrateurFromCreateUserRequest(CreateUserRequest request, @MappingTarget Administrateur admin);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "motDePasse", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "authorities", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateObservateurFromCreateUserRequest(CreateUserRequest request, @MappingTarget Observateur observateur);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "motDePasse", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "collegeElectoral", ignore = true)
    @Mapping(target = "emargements", ignore = true)
    @Mapping(target = "otpCode", ignore = true)
    @Mapping(target = "otpExpiry", ignore = true)
    @Mapping(target = "otpVerified", ignore = true)
    @Mapping(target = "authorities", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateElecteurFromRegisterRequest(RegisterRequest request, @MappingTarget Electeur electeur);
}

