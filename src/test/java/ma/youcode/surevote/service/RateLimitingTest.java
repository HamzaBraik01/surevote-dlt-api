package ma.youcode.surevote.service;

import ma.youcode.surevote.config.RateLimitingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RateLimitingConfig bucket behavior.
 */
class RateLimitingTest {

    private RateLimitingConfig rateLimiting;

    @BeforeEach
    void setUp() {
        rateLimiting = new RateLimitingConfig();
        rateLimiting.clearBuckets();
    }

    @Test
    void loginBucket_allows5RequestsThenBlocks() {
        String ip = "192.168.1.1";
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiting.loginBucket(ip).tryConsume(1)).isTrue();
        }
        // 6th request should be blocked
        assertThat(rateLimiting.loginBucket(ip).tryConsume(1)).isFalse();
    }

    @Test
    void loginBucket_differentIps_areIndependent() {
        for (int i = 0; i < 5; i++) {
            rateLimiting.loginBucket("10.0.0.1").tryConsume(1);
        }
        // Different IP should still have full quota
        assertThat(rateLimiting.loginBucket("10.0.0.2").tryConsume(1)).isTrue();
    }

    @Test
    void twoFactorBucket_allows10RequestsThenBlocks() {
        String ip = "192.168.1.2";
        for (int i = 0; i < 10; i++) {
            assertThat(rateLimiting.twoFactorBucket(ip).tryConsume(1)).isTrue();
        }
        assertThat(rateLimiting.twoFactorBucket(ip).tryConsume(1)).isFalse();
    }

    @Test
    void passwordResetBucket_allows3RequestsThenBlocks() {
        String email = "voter@test.ma";
        for (int i = 0; i < 3; i++) {
            assertThat(rateLimiting.passwordResetBucket(email).tryConsume(1)).isTrue();
        }
        assertThat(rateLimiting.passwordResetBucket(email).tryConsume(1)).isFalse();
    }
}
