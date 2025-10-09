package ma.youcode.surevote.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration class that enables Spring's scheduling and AOP proxy support.
 *
 * @EnableScheduling activates the @Scheduled annotation processing,
 * which is required for the election state machine automated transitions
 * (ElectionService.updateElectionStatuses runs every 60 seconds).
 *
 * @EnableAspectJAutoProxy activates AspectJ-based AOP proxy creation,
 * which is required for the AuditAspect to intercept @Auditable methods.
 * This replaces the auto-configuration that was previously provided by
 * spring-boot-starter-aop.
 */
@Configuration
@EnableScheduling
@EnableAspectJAutoProxy
public class SchedulingConfig {
    // Enables @Scheduled tasks for election state machine automation
    // See ElectionService.updateElectionStatuses() for implementation

    // Enables @Aspect processing for AuditAspect
    // See AuditAspect for the immutable audit trail implementation
}
