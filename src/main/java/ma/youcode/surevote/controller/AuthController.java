package ma.youcode.surevote.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.config.RateLimitingConfig;
import ma.youcode.surevote.domain.entity.Electeur;
import ma.youcode.surevote.domain.entity.Utilisateur;
import ma.youcode.surevote.dto.request.LoginRequest;
import ma.youcode.surevote.dto.request.OtpVerificationRequest;
import ma.youcode.surevote.dto.request.RegisterRequest;
import ma.youcode.surevote.dto.response.AuthResponse;
import ma.youcode.surevote.dto.response.endpoint.auth.CurrentUserResponse;
import ma.youcode.surevote.exception.JwtAuthenticationException;
import ma.youcode.surevote.mapper.endpoint.AuthEndpointMapper;
import ma.youcode.surevote.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * REST controller for all authentication operations in the SUREVOTE platform.
 *
 * Public endpoints (no authentication required):
 *   POST /api/auth/register     — Register a new user account
 *   POST /api/auth/login        — Authenticate and receive JWT tokens
 *   POST /api/auth/refresh      — Exchange a refresh token for a new access token
 *
 * Protected endpoints (JWT required):
 *   POST /api/auth/2fa/verify   — Verify OTP code for Two-Factor Authentication (ELECTEUR only)
 *   POST /api/auth/2fa/resend   — Resend a new OTP code (ELECTEUR only)
 *   GET  /api/auth/me           — Get the currently authenticated user's profile
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "Authentication",
    description = "Endpoints for user registration, login, token refresh, and Two-Factor Authentication (2FA)."
)
public class AuthController {

    private final AuthService authService;
    private final RateLimitingConfig rateLimiting;
    private final AuthEndpointMapper authEndpointMapper;

    // =========================================================
    // Registration
    // =========================================================

    /**
     * Registers a new user on the SUREVOTE platform.
     *
     * The role defaults to ELECTEUR if not specified in the request body.
     * Passwords are BCrypt-hashed server-side — never stored in plain text.
     *
     * Returns HTTP 201 Created with the JWT tokens and user profile snapshot,
     * allowing the client to immediately bootstrap a session after registration.
     *
     * @param request the registration form data (validated)
     * @return 201 with AuthResponse, or 409 if email/CIN already exists
     */
    @PostMapping("/register")
    @Operation(
        summary = "Register a new user",
        description = """
            Creates a new user account on the SUREVOTE platform.

            - The `role` field defaults to `ELECTEUR` if not provided.
            - Password is BCrypt-hashed before storage (never stored in plain text).
            - Returns JWT access + refresh tokens immediately upon successful registration.
            - Returns **409 Conflict** if the email or CIN is already registered.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User registered successfully",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation failed — invalid fields"),
        @ApiResponse(responseCode = "409", description = "Email or CIN already registered")
    })
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        String ip = resolveClientIp(httpRequest);
        if (!rateLimiting.registerBucket(ip).tryConsume(1)) {
            log.warn("Rate limit exceeded for registration from IP: {}", ip);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        log.info("Registration request received for email: {}", request.getEmail());
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =========================================================
    // Login
    // =========================================================

    /**
     * Authenticates a user with their email and password.
     *
     * On success, returns signed JWT access and refresh tokens, along with
     * a snapshot of the user's profile (id, role, 2FA status for ELECTEUR).
     *
     * If the authenticated user is an ELECTEUR with 2FA enabled
     * (doubleFacteurActif = true), an OTP code is dispatched to their email.
     * The response will indicate otpVerified = false, signalling that the Angular
     * client must redirect to the /2fa/verify page before granting ballot access.
     *
     * @param request login credentials (email + password)
     * @return 200 with AuthResponse containing JWT tokens and user profile
     */
    @PostMapping("/login")
    @Operation(
        summary = "Authenticate and receive JWT tokens",
        description = """
            Authenticates a user with their email address and password.

            **Response includes:**
            - `accessToken`: short-lived JWT (default: 60 minutes)
            - `refreshToken`: long-lived refresh token (default: 7 days)
            - `role`: the user's assigned role (`ADMIN`, `ELECTEUR`, `OBSERVATEUR`)
            - `doubleFacteurActif`: for `ELECTEUR` users — whether 2FA is required
            - `otpVerified`: for `ELECTEUR` users — whether OTP has been verified

            **2FA flow:** If `doubleFacteurActif = true`, an OTP is sent to the user's
            email. The Angular client must call `POST /api/auth/2fa/verify` before
            allowing access to the voting booth.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Authentication successful",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials or account disabled"),
        @ApiResponse(responseCode = "400", description = "Validation failed — missing or invalid fields")
    })
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String ip = resolveClientIp(httpRequest);
        if (!rateLimiting.loginBucket(ip).tryConsume(1)) {
            log.warn("Rate limit exceeded for login from IP: {}", ip);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        log.info("Login attempt for email: {}", request.email());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    // =========================================================
    // Token Refresh
    // =========================================================

    /**
     * Exchanges a valid refresh token for a new access token.
     *
     * The refresh token must be provided either:
     *   - In the Authorization header as: Bearer <refreshToken>
     *   - In the request body as a plain JSON string: { "refreshToken": "..." }
     *
     * This endpoint allows the Angular HTTP interceptor to transparently
     * renew sessions without forcing the user to re-authenticate.
     *
     * @param request     HTTP request (for Authorization header extraction)
     * @param requestBody optional JSON body containing the refresh token
     * @return 200 with new AuthResponse containing fresh tokens
     */
    @PostMapping("/refresh")
    @Operation(
        summary = "Refresh expired JWT access token",
        description = """
            Issues a new access token using a valid refresh token.

            Provide the refresh token via:
            - `Authorization: Bearer <refreshToken>` header, OR
            - Request body: `{ "refreshToken": "<token>" }`

            Returns a new `accessToken` and `refreshToken` pair.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token refreshed successfully",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "401", description = "Refresh token invalid, expired, or missing")
    })
    public ResponseEntity<AuthResponse> refresh(
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, String> requestBody) {

        String refreshToken = extractRefreshToken(request, requestBody);

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new JwtAuthenticationException(
                "Le token de rafraîchissement est manquant. " +
                "Fournissez-le via l'en-tête Authorization ou le corps de la requête."
            );
        }

        log.debug("Token refresh request received");
        AuthResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(
        summary = "Logout and revoke current JWT",
        description = "Invalidates the provided access token server-side by blacklisting it until expiration."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Logout successful"),
        @ApiResponse(responseCode = "401", description = "Token missing, invalid, or expired")
    })
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        String token = extractBearerToken(request);
        if (!StringUtils.hasText(token)) {
            throw new JwtAuthenticationException("Token d'accès manquant dans l'en-tête Authorization.");
        }

        authService.logout(token);
        return ResponseEntity.ok(Map.of("message", "Déconnexion effectuée avec succès."));
    }

    // =========================================================
    // Two-Factor Authentication (2FA)
    // =========================================================

    /**
     * Verifies the OTP code submitted by an ELECTEUR voter.
     *
     * This endpoint is called after a successful login when the voter has
     * 2FA enabled. The voter enters the 6-digit code received via email.
     *
     * On success:
     *  - The voter's otpVerified flag is set to true.
     *  - Fresh JWT tokens are returned.
     *  - The Angular client can now grant access to the voting booth.
     *
     * @param request the OTP verification form (6-digit code)
     * @param currentUser the currently authenticated user (from JWT)
     * @return 200 with updated AuthResponse confirming OTP verification
     */
    @PostMapping("/2fa/verify")
    @Operation(
        summary = "Verify Two-Factor Authentication OTP",
        description = """
            Verifies the 6-digit OTP code sent to the voter's email address.

            **Required:** Valid JWT in `Authorization: Bearer <token>` header.

            On success, returns an updated `AuthResponse` with `otpVerified = true`,
            which unlocks access to the `/api/vote/**` endpoints.

            The OTP expires after **5 minutes**. Call `POST /api/auth/2fa/resend`
            to receive a fresh code if the current one has expired.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OTP verified — 2FA complete",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "401", description = "OTP code incorrect or expired"),
        @ApiResponse(responseCode = "403", description = "Not an ELECTEUR role")
    })
    public ResponseEntity<AuthResponse> verifyOtp(
            @Valid @RequestBody OtpVerificationRequest request,
            @AuthenticationPrincipal Utilisateur currentUser,
            HttpServletRequest httpRequest) {

        if (!(currentUser instanceof Electeur electeur)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String ip = resolveClientIp(httpRequest);
        if (!rateLimiting.twoFactorBucket(ip).tryConsume(1)) {
            log.warn("Rate limit exceeded for 2FA from IP: {}", ip);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        log.info("2FA OTP verification attempt for voter: {}", currentUser.getEmail());
        AuthResponse response = authService.verifyOtp(request, electeur);
        return ResponseEntity.ok(response);
    }

    /**
     * Resends a new OTP to the authenticated voter's registered email address.
     *
     * The previous OTP is invalidated and a new 6-digit code is generated
     * and dispatched. The new OTP has a fresh 5-minute validity window.
     *
     * @param currentUser the currently authenticated voter (from JWT)
     * @return 200 with a success message
     */
    @PostMapping("/2fa/resend")
    @Operation(
        summary = "Resend 2FA OTP code",
        description = """
            Generates a new OTP code and sends it to the voter's registered email.
            The previous OTP is invalidated.

            **Required:** Valid JWT in `Authorization: Bearer <token>` header.

            Use this endpoint when the original OTP has expired or was not received.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OTP resent successfully"),
        @ApiResponse(responseCode = "403", description = "Not an ELECTEUR role"),
        @ApiResponse(responseCode = "500", description = "Email delivery failure")
    })
    public ResponseEntity<Map<String, String>> resendOtp(
            @AuthenticationPrincipal Utilisateur currentUser) {

        if (!(currentUser instanceof Electeur electeur)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        log.info("OTP resend requested for voter: {}", currentUser.getEmail());
        authService.resendOtp(electeur);

        return ResponseEntity.ok(Map.of(
            "message", "Un nouveau code OTP a été envoyé à votre adresse email.",
            "email", maskEmail(currentUser.getEmail())
        ));
    }

    // =========================================================
    // Current User Profile
    // =========================================================

    /**
     * Returns the profile of the currently authenticated user.
     *
     * Useful for the Angular client to refresh the user session state
     * after a page reload, without requiring a full re-login.
     *
     * @param currentUser the currently authenticated user (resolved from JWT by Spring Security)
     * @return 200 with the user's profile data (no credential fields)
     */
    @GetMapping("/me")
    @Operation(
        summary = "Get current authenticated user's profile",
        description = """
            Returns the public profile of the currently authenticated user.

            **Required:** Valid JWT in `Authorization: Bearer <token>` header.

            Response includes: id, email, nom, prenom, role, isEnabled,
            and role-specific fields (doubleFacteurActif for ELECTEUR, etc.)
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User profile returned successfully"),
        @ApiResponse(responseCode = "401", description = "JWT token missing, invalid or expired")
    })
    @Transactional(readOnly = true)
    public ResponseEntity<CurrentUserResponse> getCurrentUser(
            @AuthenticationPrincipal Utilisateur currentUser) {

        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        CurrentUserResponse profile = authEndpointMapper.toCurrentUserResponse(currentUser);
        return ResponseEntity.ok(profile);
    }

    // =========================================================
    // Private helpers
    // =========================================================

    /**
     * Extracts the refresh token from the Authorization header or request body.
     *
     * Priority:
     *  1. Authorization: Bearer <token> header (standard usage)
     *  2. Request body field "refreshToken" (fallback for clients that can't set headers)
     *
     * @param request     the incoming HTTP request
     * @param requestBody optional JSON body map
     * @return the extracted refresh token string, or null if not found
     */
    private String extractRefreshToken(HttpServletRequest request, Map<String, String> requestBody) {
        // Attempt 1: Authorization header
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }

        // Attempt 2: Request body
        if (requestBody != null && requestBody.containsKey("refreshToken")) {
            return requestBody.get("refreshToken");
        }

        return null;
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        return null;
    }

    /**
     * Masks an email address for privacy-safe logging and response messages.
     * Example: "voter@example.com" → "vo***@example.com"
     *
     * @param email the email address to mask
     * @return the partially masked email string
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@");
        String local = parts[0];
        String maskedLocal = local.length() <= 2
                ? local.charAt(0) + "***"
                : local.substring(0, 2) + "***";
        return maskedLocal + "@" + parts[1];
    }
}
