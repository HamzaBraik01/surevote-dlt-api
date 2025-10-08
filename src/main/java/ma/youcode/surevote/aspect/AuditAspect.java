package ma.youcode.surevote.aspect;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.annotation.Auditable;
import ma.youcode.surevote.domain.entity.LogAudit;
import ma.youcode.surevote.domain.entity.Utilisateur;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogWriter auditLogWriter;

    /**
     * Intercepts methods annotated with @Auditable and logs successful execution.
     */
    @AfterReturning("@annotation(auditable)")
    public void logSuccessfulAction(JoinPoint joinPoint, Auditable auditable) {
        try {
            AuthContext ctx = extractAuthContext();
            String details = buildDetails(auditable.description(), joinPoint, null);
            String actionType = auditable.actionType().isBlank()
                    ? joinPoint.getSignature().getName().toUpperCase()
                    : auditable.actionType();

            LogAudit entry = LogAudit.of(
                    actionType,
                    details,
                    ctx.ip(),
                    ctx.userId(),
                    ctx.email()
            );
            auditLogWriter.write(entry);
        } catch (Exception e) {
            // Never let audit logging disrupt the main flow
            log.error("Failed to write audit log for [{}]: {}", joinPoint.getSignature().getName(), e.getMessage());
        }
    }

    /**
     * Intercepts methods annotated with @Auditable and logs exceptions.
     */
    @AfterThrowing(pointcut = "@annotation(auditable)", throwing = "ex")
    public void logFailedAction(JoinPoint joinPoint, Auditable auditable, Throwable ex) {
        try {
            AuthContext ctx = extractAuthContext();
            String actionType = (auditable.actionType().isBlank()
                    ? joinPoint.getSignature().getName().toUpperCase()
                    : auditable.actionType()) + "_FAILURE";

            String details = buildDetails(auditable.description(), joinPoint, ex);

            LogAudit logEntry = LogAudit.of(
                    actionType,
                    details,
                    ctx.ip(),
                    ctx.userId(),
                    ctx.email()
            );
            auditLogWriter.write(logEntry);
        } catch (Exception e) {
            log.error("Failed to write failure audit log: {}", e.getMessage());
        }
    }

    /**
     * Intercepts login attempts (AuthService.login) for audit regardless of @Auditable.
     */
    @AfterReturning("execution(* ma.youcode.surevote.service.AuthService.login(..))")
    public void logLoginSuccess(JoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            String email = args.length > 0 ? getFirstArg(args) : "unknown";
            String ip = getClientIp();
            auditLogWriter.write(LogAudit.of("LOGIN_SUCCESS",
                    "Connexion réussie pour: " + email, ip, null, email));
        } catch (Exception ignored) {}
    }

    @AfterThrowing(pointcut = "execution(* ma.youcode.surevote.service.AuthService.login(..))", throwing = "ex")
    public void logLoginFailure(JoinPoint joinPoint, Throwable ex) {
        try {
            Object[] args = joinPoint.getArgs();
            String email = args.length > 0 ? getFirstArg(args) : "unknown";
            String ip = getClientIp();
            auditLogWriter.write(LogAudit.of("LOGIN_FAILURE",
                    "Échec de connexion pour: " + email + " | Raison: " + ex.getMessage(),
                    ip, null, email));
        } catch (Exception ignored) {}
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private record AuthContext(Long userId, String email, String ip) {}

    private AuthContext extractAuthContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = null;
        String email = "anonymous";

        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Utilisateur user) {
            userId = user.getId();
            email = user.getEmail();
        }

        return new AuthContext(userId, email, getClientIp());
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String forwarded = req.getHeader("X-Forwarded-For");
                return (forwarded != null && !forwarded.isBlank())
                        ? forwarded.split(",")[0].trim()
                        : req.getRemoteAddr();
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    private String buildDetails(String description, JoinPoint joinPoint, Throwable ex) {
        StringBuilder sb = new StringBuilder();
        if (!description.isBlank()) sb.append(description).append(" | ");
        sb.append("method=").append(joinPoint.getSignature().getName());
        if (ex != null) sb.append(" | error=").append(ex.getMessage());
        return sb.toString();
    }

    private String getFirstArg(Object[] args) {
        if (args[0] == null) return "null";
        // If it's a record/DTO with email, try to extract it
        try {
            return args[0].getClass().getMethod("email").invoke(args[0]).toString();
        } catch (Exception ignored) {
            return args[0].toString();
        }
    }
}
