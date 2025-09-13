package ma.youcode.surevote.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting service using Bucket4j token-bucket algorithm.
 * Injected directly into controllers to enforce per-IP / per-key limits.
 *
 * Limits:
 *  - Login:          5 attempts / minute  / IP
 *  - 2FA verify:    10 attempts / 5 min   / IP
 *  - Password reset: 3 requests / hour    / email
 */
@Service
public class RateLimitingConfig {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /** Login rate limiter: 5 requests per minute per IP. */
    public Bucket loginBucket(String ip) {
        return buckets.computeIfAbsent(ip + ":login", k ->
            Bucket.builder()
                .addLimit(Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1))))
                .build()
        );
    }

    /** 2FA verification rate limiter: 10 requests per 5 minutes per IP. */
    public Bucket twoFactorBucket(String ip) {
        return buckets.computeIfAbsent(ip + ":2fa", k ->
            Bucket.builder()
                .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(5))))
                .build()
        );
    }

    /** Password reset rate limiter: 3 requests per hour per email. */
    public Bucket passwordResetBucket(String email) {
        return buckets.computeIfAbsent(email + ":reset", k ->
            Bucket.builder()
                .addLimit(Bandwidth.classic(3, Refill.intervally(3, Duration.ofHours(1))))
                .build()
        );
    }

    /** Registration rate limiter: 5 requests per minute per IP. */
    public Bucket registerBucket(String ip) {
        return buckets.computeIfAbsent(ip + ":register", k ->
            Bucket.builder()
                .addLimit(Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1))))
                .build()
        );
    }

    /** Vote submission rate limiter: 10 requests per 5 minutes per IP. */
    public Bucket voteSubmitBucket(String ip) {
        return buckets.computeIfAbsent(ip + ":vote", k ->
            Bucket.builder()
                .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(5))))
                .build()
        );
    }

    /** Clears all buckets — for testing only. */
    public void clearBuckets() {
        buckets.clear();
    }
}
