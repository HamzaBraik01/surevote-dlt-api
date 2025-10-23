package ma.youcode.surevote.security;

import ma.youcode.surevote.domain.entity.Electeur;
import ma.youcode.surevote.domain.entity.Utilisateur;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import ma.youcode.surevote.repository.UtilisateurRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock private UtilisateurRepository utilisateurRepository;
    @InjectMocks private UserDetailsServiceImpl service;

    @Test
    void loadUserByUsername_existingUser_returnsUserDetails() {
        Electeur electeur = new Electeur();
        electeur.setEmail("user@test.com");
        electeur.setMotDePasse("hashed");
        electeur.setRole(RoleUtilisateur.ELECTEUR);
        when(utilisateurRepository.findByEmail("user@test.com")).thenReturn(Optional.of(electeur));

        UserDetails result = service.loadUserByUsername("user@test.com");

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("user@test.com");
    }

    @Test
    void loadUserByUsername_nonExistingUser_throwsUsernameNotFoundException() {
        when(utilisateurRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("unknown@test.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("unknown@test.com");
    }
}
