package ma.youcode.surevote.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Abstract base entity for all authenticated users in SUREVOTE.
 * Uses SINGLE_TABLE inheritance — all user types are stored in one table
 * with a 'dtype' discriminator column for performance and simplicity.
 *
 * Implements Spring Security's UserDetails to integrate seamlessly with
 * the authentication framework.
 */
@Entity
@Table(
    name = "utilisateurs",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_utilisateur_cin",   columnNames = "cin"),
        @UniqueConstraint(name = "uq_utilisateur_email", columnNames = "email")
    }
)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "dtype", discriminatorType = DiscriminatorType.STRING, length = 15)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class Utilisateur implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * National ID number — unique identifier per real-world person.
     */
    @Column(nullable = false, length = 20)
    private String cin;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(nullable = false, length = 100)
    private String prenom;

    /**
     * Email — used as the principal (username) for Spring Security authentication.
     */
    @Column(nullable = false, length = 150)
    private String email;

    /**
     * BCrypt-hashed password. Never stored in plain text.
     */
    @Column(name = "mot_de_passe", nullable = false)
    private String motDePasse;

    /**
     * Account activation flag. Disabled accounts cannot authenticate.
     */
    @Column(name = "is_enabled", nullable = false)
    private boolean isEnabled = true;

    /**
     * Role determines access rights via Spring Security RBAC.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoleUtilisateur role;

    // =========================================================
    // UserDetails implementation
    // =========================================================

    /**
     * Returns a single authority derived from the user's role.
     * Format: "ROLE_ADMIN", "ROLE_ELECTEUR", "ROLE_OBSERVATEUR"
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /**
     * Spring Security uses this as the password for authentication.
     */
    @Override
    public String getPassword() {
        return motDePasse;
    }

    /**
     * Spring Security uses email as the principal identifier (username).
     */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }

    // =========================================================
    // equals / hashCode — based on id only (JPA best practice)
    // =========================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Utilisateur that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id=" + id + ", email='" + email + "', role=" + role + "}";
    }
}
