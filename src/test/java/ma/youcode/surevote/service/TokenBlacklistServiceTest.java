package ma.youcode.surevote.service;

import ma.youcode.surevote.repository.RevokedTokenRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the DB-backed TokenBlacklistService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TokenBlacklistService")
class TokenBlacklistServiceTest {

    @Mock
    private RevokedTokenRepository revokedTokenRepository;

    @InjectMocks
    private TokenBlacklistService service;

    // ── blacklist ────────────────────────────────────────────────

    @Test
    @DisplayName("blacklist adds token and isBlacklisted returns true")
    void blacklist_addsToken() {
        Instant future = Instant.now().plusSeconds(3600);
        when(revokedTokenRepository.existsByTokenHash(anyString())).thenReturn(false);
        
        service.blacklist("token123", future);
        
        verify(revokedTokenRepository).save(any());
    }

    @Test
    @DisplayName("blacklist with null token does nothing")
    void blacklist_nullToken_noOp() {
        service.blacklist(null, Instant.now().plusSeconds(3600));
        verify(revokedTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("blacklist with blank token does nothing")
    void blacklist_blankToken_noOp() {
        service.blacklist("   ", Instant.now().plusSeconds(3600));
        verify(revokedTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("blacklist with null expiresAt does nothing")
    void blacklist_nullExpiry_noOp() {
        service.blacklist("someToken", null);
        verify(revokedTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("blacklist skips already-revoked token (idempotent)")
    void blacklist_alreadyRevoked_skips() {
        when(revokedTokenRepository.existsByTokenHash(anyString())).thenReturn(true);
        
        service.blacklist("token123", Instant.now().plusSeconds(3600));
        
        verify(revokedTokenRepository, never()).save(any());
    }

    // ── isBlacklisted ───────────────────────────────────────────

    @Test
    @DisplayName("isBlacklisted returns true for revoked token")
    void isBlacklisted_revokedToken_true() {
        when(revokedTokenRepository.existsByTokenHash(anyString())).thenReturn(true);
        assertThat(service.isBlacklisted("some-token")).isTrue();
    }

    @Test
    @DisplayName("isBlacklisted returns false for unknown token")
    void isBlacklisted_unknownToken_false() {
        when(revokedTokenRepository.existsByTokenHash(anyString())).thenReturn(false);
        assertThat(service.isBlacklisted("unknown")).isFalse();
    }

    @Test
    @DisplayName("isBlacklisted returns false for null token")
    void isBlacklisted_nullToken_false() {
        assertThat(service.isBlacklisted(null)).isFalse();
    }

    @Test
    @DisplayName("isBlacklisted returns false for blank token")
    void isBlacklisted_blankToken_false() {
        assertThat(service.isBlacklisted("  ")).isFalse();
    }

    // ── cleanupExpiredTokens ──────────────────────────────────────

    @Test
    @DisplayName("cleanupExpiredTokens delegates to repository")
    void cleanupExpiredTokens_delegatesToRepository() {
        when(revokedTokenRepository.deleteExpiredTokens(any(Instant.class))).thenReturn(5);
        
        service.cleanupExpiredTokens();
        
        verify(revokedTokenRepository).deleteExpiredTokens(any(Instant.class));
    }
}
