package ma.youcode.surevote.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized exception handler for the SUREVOTE REST API.
 *
 * Intercepts all exceptions thrown by controllers and services,
 * and returns a consistent, structured JSON error response.
 * Never exposes internal stack traces or sensitive system details.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // =========================================================
    // Error Response Builder
    // =========================================================

    /**
     * Builds a consistent error response body map.
     */
    private Map<String, Object> buildError(HttpStatus status, String error, String message, String path) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        body.put("path", path);
        return body;
    }

    // =========================================================
    // HTTP Request Parsing Errors
    // =========================================================

    /**
     * 400 — JSON parsing / deserialization error.
     * Triggered when the request body cannot be read (malformed JSON,
     * invalid enum value, wrong date format, etc.)
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        log.warn("Malformed request body: {} | path={}", ex.getMessage(), request.getRequestURI());

        String userMessage = "Le corps de la requête est invalide ou mal formaté.";

        // Extract useful detail from the exception message
        Throwable cause = ex.getCause();
        if (cause != null) {
            String causeMsg = cause.getMessage();
            if (causeMsg != null && causeMsg.contains("RoleUtilisateur")) {
                userMessage = "Valeur de rôle invalide. Valeurs acceptées : ADMIN, ELECTEUR, OBSERVATEUR.";
            } else if (causeMsg != null && causeMsg.contains("LocalDateTime")) {
                userMessage = "Format de date invalide. Utilisez le format ISO : yyyy-MM-dd'T'HH:mm:ss";
            }
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError(HttpStatus.BAD_REQUEST, "Bad Request",
                        userMessage, request.getRequestURI()));
    }

    // =========================================================
    // Domain / Business Logic Exceptions
    // =========================================================

    /**
     * 404 — Resource not found (Election, Candidat, User, College, etc.)
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {} | path={}", ex.getMessage(), request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(buildError(HttpStatus.NOT_FOUND, "Resource Not Found",
                        ex.getMessage(), request.getRequestURI()));
    }

    /**
     * 409 — Voter has already cast a ballot in this election.
     */
    @ExceptionHandler(AlreadyVotedException.class)
    public ResponseEntity<Map<String, Object>> handleAlreadyVoted(
            AlreadyVotedException ex, HttpServletRequest request) {
        log.warn("Duplicate vote attempt: {} | path={}", ex.getMessage(), request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(buildError(HttpStatus.CONFLICT, "Duplicate Vote",
                        ex.getMessage(), request.getRequestURI()));
    }

    /**
     * 422 — Election is not in OUVERTE state; voting is not allowed.
     */
    @ExceptionHandler(ElectionNotOpenException.class)
    public ResponseEntity<Map<String, Object>> handleElectionNotOpen(
            ElectionNotOpenException ex, HttpServletRequest request) {
        log.warn("Vote rejected — election not open: {} | path={}", ex.getMessage(), request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(buildError(HttpStatus.UNPROCESSABLE_ENTITY, "Election Not Open",
                        ex.getMessage(), request.getRequestURI()));
    }

    /**
     * 401 — Invalid or expired OTP code.
     */
    @ExceptionHandler(InvalidOtpException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidOtp(
            InvalidOtpException ex, HttpServletRequest request) {
        log.warn("Invalid OTP attempt: {} | path={}", ex.getMessage(), request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(buildError(HttpStatus.UNAUTHORIZED, "Invalid OTP",
                        ex.getMessage(), request.getRequestURI()));
    }

    /**
     * 409 — Generic conflict (duplicate email, CIN, college name, etc.)
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateResource(
            DuplicateResourceException ex, HttpServletRequest request) {
        log.warn("Duplicate resource: {} | path={}", ex.getMessage(), request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(buildError(HttpStatus.CONFLICT, "Duplicate Resource",
                        ex.getMessage(), request.getRequestURI()));
    }

    /**
     * 400 — Invalid election state machine transition.
     */
    @ExceptionHandler(InvalidElectionStateException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidElectionState(
            InvalidElectionStateException ex, HttpServletRequest request) {
        log.warn("Invalid election state transition: {} | path={}", ex.getMessage(), request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError(HttpStatus.BAD_REQUEST, "Invalid Election State",
                        ex.getMessage(), request.getRequestURI()));
    }

    /**
     * 403 — Voter is not eligible for this election (wrong college).
     */
    @ExceptionHandler(VoterNotEligibleException.class)
    public ResponseEntity<Map<String, Object>> handleVoterNotEligible(
            VoterNotEligibleException ex, HttpServletRequest request) {
        log.warn("Voter not eligible: {} | path={}", ex.getMessage(), request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(buildError(HttpStatus.FORBIDDEN, "Voter Not Eligible",
                        ex.getMessage(), request.getRequestURI()));
    }

    /**
     * 403 — 2FA not completed; voter must verify OTP before accessing the ballot.
     */
    @ExceptionHandler(TwoFactorRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleTwoFactorRequired(
            TwoFactorRequiredException ex, HttpServletRequest request) {
        log.warn("2FA required: {} | path={}", ex.getMessage(), request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(buildError(HttpStatus.FORBIDDEN, "Two-Factor Authentication Required",
                        ex.getMessage(), request.getRequestURI()));
    }

    /**
     * 422 — Results not available yet (election not yet PUBLIEE).
     */
    @ExceptionHandler(ResultsNotAvailableException.class)
    public ResponseEntity<Map<String, Object>> handleResultsNotAvailable(
            ResultsNotAvailableException ex, HttpServletRequest request) {
        log.warn("Results not available: {} | path={}", ex.getMessage(), request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(buildError(HttpStatus.UNPROCESSABLE_ENTITY, "Results Not Available",
                        ex.getMessage(), request.getRequestURI()));
    }

    // =========================================================
    // Spring Security Exceptions
    // =========================================================

    /**
     * 401 — Bad credentials during login.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Bad credentials attempt | path={}", request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(buildError(HttpStatus.UNAUTHORIZED, "Authentication Failed",
                        "Email ou mot de passe incorrect.", request.getRequestURI()));
    }

    /**
     * 401 — Account is disabled (isEnabled = false).
     */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Map<String, Object>> handleDisabledAccount(
            DisabledException ex, HttpServletRequest request) {
        log.warn("Disabled account login attempt | path={}", request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(buildError(HttpStatus.UNAUTHORIZED, "Account Disabled",
                        "Ce compte a été désactivé. Contactez l'administrateur.", request.getRequestURI()));
    }

    /**
     * 401 — Account is locked.
     */
    @ExceptionHandler(LockedException.class)
    public ResponseEntity<Map<String, Object>> handleLockedAccount(
            LockedException ex, HttpServletRequest request) {
        log.warn("Locked account login attempt | path={}", request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(buildError(HttpStatus.UNAUTHORIZED, "Account Locked",
                        "Ce compte est temporairement verrouillé.", request.getRequestURI()));
    }

    /**
     * 403 — Authenticated user does not have permission for the requested resource.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied | path={} | message={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(buildError(HttpStatus.FORBIDDEN, "Access Denied",
                        "Vous n'avez pas les droits nécessaires pour accéder à cette ressource.",
                        request.getRequestURI()));
    }

    /**
     * 401 — JWT token invalid, expired, or missing.
     */
    @ExceptionHandler(JwtAuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleJwtAuthentication(
            JwtAuthenticationException ex, HttpServletRequest request) {
        log.warn("JWT authentication failed: {} | path={}", ex.getMessage(), request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(buildError(HttpStatus.UNAUTHORIZED, "JWT Authentication Failed",
                        ex.getMessage(), request.getRequestURI()));
    }

    // =========================================================
    // Validation Exceptions
    // =========================================================

    /**
     * 400 — @Valid / @Validated DTO validation failure.
     * Returns a map of field → error message for each invalid field.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        log.debug("Validation failed: {} | path={}", fieldErrors, request.getRequestURI());

        Map<String, Object> body = buildError(
                HttpStatus.BAD_REQUEST,
                "Validation Failed",
                "Un ou plusieurs champs sont invalides.",
                request.getRequestURI()
        );
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 400 — Bean validation constraint violations (e.g., path variable constraints).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> violations = new HashMap<>();
        ex.getConstraintViolations().forEach(cv ->
                violations.put(cv.getPropertyPath().toString(), cv.getMessage()));
        log.debug("Constraint violation: {} | path={}", violations, request.getRequestURI());

        Map<String, Object> body = buildError(
                HttpStatus.BAD_REQUEST,
                "Constraint Violation",
                "Validation des données échouée.",
                request.getRequestURI()
        );
        body.put("violations", violations);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 400 — Type mismatch in request parameters (e.g., String instead of Long for an ID).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = String.format(
                "Le paramètre '%s' doit être de type '%s'.",
                ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "inconnu"
        );
        log.debug("Type mismatch: {} | path={}", message, request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError(HttpStatus.BAD_REQUEST, "Type Mismatch", message, request.getRequestURI()));
    }

    /**
     * 400 — Illegal state transition or general illegal argument.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(
            IllegalStateException ex, HttpServletRequest request) {
        log.warn("Illegal state: {} | path={}", ex.getMessage(), request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError(HttpStatus.BAD_REQUEST, "Illegal State",
                        ex.getMessage(), request.getRequestURI()));
    }

    /**
     * 400 — Illegal argument passed to a method.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument: {} | path={}", ex.getMessage(), request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError(HttpStatus.BAD_REQUEST, "Invalid Argument",
                        ex.getMessage(), request.getRequestURI()));
    }

    /**
     * Catch-all for runtime exceptions that may include:
     * - PropertyReferenceException (invalid sort field) → 400
     * - LazyInitializationException (Hibernate session closed) → 500
     * - Other uncaught RuntimeExceptions → 500
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(
            RuntimeException ex, HttpServletRequest request) {
        String exClassName = ex.getClass().getSimpleName();

        // Invalid sort field in paginated queries
        if ("PropertyReferenceException".equals(exClassName)) {
            log.warn("Invalid property reference: {} | path={}", ex.getMessage(), request.getRequestURI());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(buildError(HttpStatus.BAD_REQUEST, "Invalid Sort Field",
                            "Le champ de tri spécifié est invalide.",
                            request.getRequestURI()));
        }

        // Hibernate lazy loading failure
        if ("LazyInitializationException".equals(exClassName)) {
            log.error("LazyInitializationException | path={} | message={}", request.getRequestURI(), ex.getMessage(), ex);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Data Loading Error",
                            "Erreur de chargement des données. Veuillez réessayer.",
                            request.getRequestURI()));
        }

        // Transaction rollback (e.g., audit logging in read-only transaction)
        if ("UnexpectedRollbackException".equals(exClassName)) {
            log.error("Transaction rollback | path={} | message={}", request.getRequestURI(), ex.getMessage(), ex);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Transaction Error",
                            "L'opération a été annulée en raison d'un conflit transactionnel. Veuillez réessayer.",
                            request.getRequestURI()));
        }

        // Default: log and return 500
        log.error("Unhandled runtime exception | path={} | type={} | message={}",
                request.getRequestURI(), ex.getClass().getName(), ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                        "Une erreur inattendue s'est produite. Veuillez réessayer ou contacter le support.",
                        request.getRequestURI()));
    }

    // =========================================================
    // Fallback — Catch-All
    // =========================================================

    /**
     * 500 — Any unhandled exception.
     * Returns a generic error message; never exposes internal details.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception | path={} | type={} | message={}",
                request.getRequestURI(), ex.getClass().getName(), ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Internal Server Error",
                        "Une erreur inattendue s'est produite. Veuillez réessayer ou contacter le support.",
                        request.getRequestURI()
                ));
    }
}
