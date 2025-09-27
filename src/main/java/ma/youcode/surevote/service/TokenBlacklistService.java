package ma.youcode.surevote.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.domain.entity.RevokedToken;
import ma.youcode.surevote.repository.RevokedTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * Database-backed JWT token revocation service.
 *
 * Stores SHA-256 hashes of revoked tokens in the `revoked_tokens` table.
 * A scheduled cleanup task removes expired entries every 30 minutes.
 *
 * This replaces the previous in-memory ConcurrentHashMap implementation
 * to ensure:
 *  - Token revocations survive server restarts
 *  - Consistent behavior across multiple application instances
 *  - No memory leaks from unbounded token accumulation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private final RevokedTokenRepository revokedTokenRepository;

    /**
     * Revokes a JWT token by storing its SHA-256 hash in the database.
     *
     * @param token     the raw JWT token string to revoke
     * @param expiresAt when the token naturally expires (for cleanup scheduling)
     */
    @Transactional
    public void blacklist(String token, Instant expiresAt) {
        if (token == null || token.isBlank() || expiresAt == null) {
            return;
        }

        String hash = hashToken(token);

        // Idempotency: skip if already revoked
        if (revokedTokenRepository.existsByTokenHash(hash)) {
            log.debug("Token already blacklisted — skipping");
            return;
        }

        RevokedToken revoked = RevokedToken.builder()
                .tokenHash(hash)
                .expiresAt(expiresAt)
                .revokedAt(Instant.now())
                .build();

        revokedTokenRepository.save(revoked);
        log.debug("Token blacklisted until {}", expiresAt);
    }

    /**
     * Checks whether a JWT token has been revoked.
     *
     * @param token the raw JWT token string to check
     * @return true if the token has been revoked and has not yet expired
     */
    @Transactional(readOnly = true)
    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return revokedTokenRepository.existsByTokenHash(hashToken(token));
    }

    /**
     * Removes expired revocation entries from the database.
     * Runs every 30 minutes to keep the table lean.
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes
    @Transactional
    public void cleanupExpiredTokens() {
        int deleted = revokedTokenRepository.deleteExpiredTokens(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired revoked token(s)", deleted);
        }
    }

    /**
     * Computes a SHA-256 hash of the raw JWT token.
     * We never store the raw token — only a one-way hash.
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}