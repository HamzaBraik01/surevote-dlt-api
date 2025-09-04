package ma.youcode.surevote.repository;

import ma.youcode.surevote.domain.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for PasswordResetToken entities.
 * Handles all database operations for password reset tokens.
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Finds a password reset token by its token string.
     *
     * @param token the reset token UUID
     * @return Optional containing the token if found
     */
    Optional<PasswordResetToken> findByToken(String token);

    /**
     * Deletes all expired and unused tokens.
     * Called periodically to clean up stale records.
     *
     * @param now the current timestamp
     */
    @Query("DELETE FROM PasswordResetToken p WHERE p.expiresAt < ?1 AND p.used = false")
    void deleteExpiredTokens(LocalDateTime now);

    /**
     * Counts the number of valid (non-expired, unused) reset tokens for a user.
     *
     * @param userId the user's ID
     * @param now the current timestamp
     * @return the count of valid tokens
     */
    @Query("SELECT COUNT(p) FROM PasswordResetToken p WHERE p.utilisateur.id = ?1 AND p.expiresAt > ?2 AND p.used = false")
    long countValidTokensByUserId(Long userId, LocalDateTime now);
}
