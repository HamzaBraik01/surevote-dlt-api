package ma.youcode.surevote.mapper.endpoint;

import ma.youcode.surevote.domain.entity.Administrateur;
import ma.youcode.surevote.domain.entity.CollegeElectoral;
import ma.youcode.surevote.domain.entity.Electeur;
import ma.youcode.surevote.domain.entity.Observateur;
import ma.youcode.surevote.domain.entity.Utilisateur;
import ma.youcode.surevote.dto.response.endpoint.auth.CurrentUserResponse;
import ma.youcode.surevote.mapper.MapStructConfig;
import org.hibernate.Hibernate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface AuthEndpointMapper {

    @Mapping(source = "id", target = "userId")
    @Mapping(target = "telephone", ignore = true)
    @Mapping(target = "doubleFacteurActif", ignore = true)
    @Mapping(target = "otpVerified", ignore = true)
    @Mapping(target = "collegeElectoralId", ignore = true)
    @Mapping(target = "collegeElectoralNom", ignore = true)
    @Mapping(target = "departement", ignore = true)
    @Mapping(target = "organisme", ignore = true)
    CurrentUserResponse toCurrentUserResponseBase(Utilisateur user);

    // Electeur mapping — collegeElectoral is handled manually (lazy-safe)
    @Mapping(source = "id", target = "userId")
    @Mapping(target = "collegeElectoralId", ignore = true)
    @Mapping(target = "collegeElectoralNom", ignore = true)
    @Mapping(target = "departement", ignore = true)
    @Mapping(target = "organisme", ignore = true)
    CurrentUserResponse mapElecteurBase(Electeur electeur);

    @Mapping(source = "id", target = "userId")
    @Mapping(target = "telephone", ignore = true)
    @Mapping(target = "doubleFacteurActif", ignore = true)
    @Mapping(target = "otpVerified", ignore = true)
    @Mapping(target = "collegeElectoralId", ignore = true)
    @Mapping(target = "collegeElectoralNom", ignore = true)
    @Mapping(target = "organisme", ignore = true)
    CurrentUserResponse toCurrentUserResponse(Administrateur admin);

    @Mapping(source = "id", target = "userId")
    @Mapping(target = "telephone", ignore = true)
    @Mapping(target = "doubleFacteurActif", ignore = true)
    @Mapping(target = "otpVerified", ignore = true)
    @Mapping(target = "collegeElectoralId", ignore = true)
    @Mapping(target = "collegeElectoralNom", ignore = true)
    @Mapping(target = "departement", ignore = true)
    CurrentUserResponse toCurrentUserResponse(Observateur observateur);

    /**
     * Dispatches to the correct mapping method based on runtime type.
     * For Electeur, uses the lazy-safe manual method that guards
     * against LazyInitializationException on collegeElectoral.
     */
    default CurrentUserResponse toCurrentUserResponse(Utilisateur user) {
        if (user == null) return null;
        if (user instanceof Electeur e) {
            CurrentUserResponse resp = mapElecteurBase(e);
            // Safely access lazy-loaded collegeElectoral
            try {
                CollegeElectoral college = e.getCollegeElectoral();
                if (college != null && Hibernate.isInitialized(college)) {
                    resp.setCollegeElectoralId(college.getId());
                    resp.setCollegeElectoralNom(college.getNom());
                }
            } catch (Exception ignored) {
                // LazyInitializationException — leave fields null
            }
            return resp;
        }
        if (user instanceof Administrateur a) return toCurrentUserResponse(a);
        if (user instanceof Observateur o) return toCurrentUserResponse(o);
        return toCurrentUserResponseBase(user);
    }
}

