package ma.youcode.surevote.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Persisted record of a revoked (blacklisted) JWT token.
 *
 * Stores the SHA-256 hash of the token (never the raw token itself)
 * along with its expiration timestamp. A scheduled cleanup task
 * removes expired entries periodically.
 *
 * Replaces the previous in-memory ConcurrentHashMap implementation
 * to ensure revocation survives server restarts and works across
 * multiple application instances.
 */
@Entity
@Table(
    name = "revoked_tokens",
    indexes = {
        @Index(name = "idx_revoked_token_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_revoked_token_expires", columnList = "expires_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevokedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * SHA-256 hash of the revoked JWT token.
     * We never store the raw token — only a one-way hash for lookup.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /**
     * When the original JWT token expires naturally.
     * Used by the cleanup scheduler to remove entries whose tokens
     * would have expired anyway.
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * When the token was revoked (blacklisted).
     */
    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;

    @PrePersist
    protected void onPersist() {
        if (revokedAt == null) {
            revokedAt = Instant.now();
        }
    }
}
