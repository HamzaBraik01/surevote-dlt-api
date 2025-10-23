package ma.youcode.surevote.mapper;

import ma.youcode.surevote.domain.entity.Administrateur;
import ma.youcode.surevote.domain.entity.Electeur;
import ma.youcode.surevote.domain.entity.Observateur;
import ma.youcode.surevote.domain.entity.Utilisateur;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import ma.youcode.surevote.dto.request.CreateUserRequest;
import ma.youcode.surevote.dto.request.RegisterRequest;
import ma.youcode.surevote.dto.response.UserResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UtilisateurMapperTest {

    /**
     * Tests the 'default' dispatcher method toResponse(Utilisateur)
     */
    private final UtilisateurMapper mapper = new UtilisateurMapper() {
        @Override
        public UserResponse toBaseResponse(Utilisateur user) {
            UserResponse r = new UserResponse();
            r.setEmail(user.getEmail());
            return r;
        }

        @Override
        public UserResponse toResponse(Electeur electeur) {
            UserResponse r = new UserResponse();
            r.setEmail(electeur.getEmail());
            return r;
        }

        @Override
        public UserResponse toResponse(Administrateur admin) {
            UserResponse r = new UserResponse();
            r.setEmail(admin.getEmail());
            return r;
        }

        @Override
        public UserResponse toResponse(Observateur observateur) {
            UserResponse r = new UserResponse();
            r.setEmail(observateur.getEmail());
            return r;
        }

        @Override
        public void updateElecteurFromCreateUserRequest(CreateUserRequest request, Electeur electeur) {}
        @Override
        public void updateAdministrateurFromCreateUserRequest(CreateUserRequest request, Administrateur admin) {}
        @Override
        public void updateObservateurFromCreateUserRequest(CreateUserRequest request, Observateur observateur) {}
        @Override
        public void updateElecteurFromRegisterRequest(RegisterRequest request, Electeur electeur) {}
    };

    @Test
    void toResponse_withNull_returnsNull() {
        assertThat(mapper.toResponse((Utilisateur) null)).isNull();
    }

    @Test
    void toResponse_withElecteur_delegatesCorrectly() {
        Electeur e = new Electeur();
        e.setEmail("voter@test.com");
        e.setRole(RoleUtilisateur.ELECTEUR);
        UserResponse response = mapper.toResponse((Utilisateur) e);
        assertThat(response.getEmail()).isEqualTo("voter@test.com");
    }

    @Test
    void toResponse_withAdmin_delegatesCorrectly() {
        Administrateur a = new Administrateur();
        a.setEmail("admin@test.com");
        a.setRole(RoleUtilisateur.ADMIN);
        UserResponse response = mapper.toResponse((Utilisateur) a);
        assertThat(response.getEmail()).isEqualTo("admin@test.com");
    }

    @Test
    void toResponse_withObservateur_delegatesCorrectly() {
        Observateur o = new Observateur();
        o.setEmail("obs@test.com");
        o.setRole(RoleUtilisateur.OBSERVATEUR);
        UserResponse response = mapper.toResponse((Utilisateur) o);
        assertThat(response.getEmail()).isEqualTo("obs@test.com");
    }
}
