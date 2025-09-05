package ma.youcode.surevote.repository;

import ma.youcode.surevote.domain.entity.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * Repository for persistent JWT token revocation.
 *
 * Supports:
 *  - Fast existence check by token hash (indexed)
 *  - Bulk cleanup of expired entries
 */
@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, Long> {

    /**
     * Checks whether a token hash exists in the revocation table.
     *
     * @param tokenHash SHA-256 hash of the JWT token
     * @return true if the token has been revoked
     */
    boolean existsByTokenHash(String tokenHash);

    /**
     * Removes all revoked token entries whose natural expiration has passed.
     * Called by the scheduled cleanup task.
     *
     * @param now current instant
     * @return number of rows deleted
     */
    @Modifying
    @Query("DELETE FROM RevokedToken rt WHERE rt.expiresAt <= :now")
    int deleteExpiredTokens(@Param("now") Instant now);
}
