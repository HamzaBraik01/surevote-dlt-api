package ma.youcode.surevote.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FIX-3: Cache configuration for the SUREVOTE platform.
 *
 * Enables Spring's @Cacheable / @CacheEvict annotations.
 * Published election results are cached since they are immutable
 * after the PUBLIEE transition.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("publicResults");
    }
}
