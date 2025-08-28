package ma.youcode.surevote.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a password reset token issued to a user.
 *
 * Security design:
 *  - Token is a random UUID (cryptographically strong).
 *  - Token has a short TTL (default 15 minutes).
 *  - Token is single-use (cleared after successful password reset).
 *  - Token is hashed in the database (not stored in plain text).
 */
@Entity
@Table(
    name = "password_reset_tokens",
    indexes = {
        @Index(name = "idx_reset_token_user", columnList = "utilisateur_id"),
        @Index(name = "idx_reset_token_token", columnList = "token")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The random UUID token sent to the user's email.
     */
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    /**
     * The user who requested the password reset.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reset_token_user"))
    private Utilisateur utilisateur;

    /**
     * The timestamp when this token was created.
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * The timestamp when this token expires.
     * After this time, the token is invalid.
     */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /**
     * The timestamp when this token was used to reset the password.
     * Null = token has not yet been used.
     */
    @Column
    private LocalDateTime usedAt;

    /**
     * Whether this token has been consumed (already used).
     */
    @Column(nullable = false)
    private boolean used = false;

    /**
     * Checks if the token is still valid (not expired and not yet used).
     */
    public boolean isValid() {
        return LocalDateTime.now().isBefore(expiresAt) && !used;
    }
}
