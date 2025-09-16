package ma.youcode.surevote.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;

/**
 * Response DTO returned upon successful authentication.
 *
 * Contains the JWT access token, refresh token, and a snapshot
 * of the authenticated user's profile — enough for the Angular
 * client to bootstrap the session and route to the correct dashboard.
 *
 * Security notes:
 *  - The accessToken is a short-lived signed JWT (HS256).
 *  - The refreshToken has a longer TTL and is used to issue new access tokens.
 *  - Neither token embeds the user's password or any sensitive PII beyond
 *    what is needed for RBAC routing (id, email, role).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    // =========================================================
    // Token fields
    // =========================================================

    /**
     * The signed JWT access token.
     * Short-lived (default: 60 minutes).
     * Must be sent as: Authorization: Bearer <accessToken>
     */
    private String accessToken;

    /**
     * The refresh token used to obtain a new access token without re-authentication.
     * Longer-lived (default: 7 days).
     * Sent to POST /api/auth/refresh
     */
    private String refreshToken;

    /**
     * Token type — always "Bearer" for JWT-based authentication.
     * Included as a hint for client-side header construction.
     */
    @Builder.Default
    private String tokenType = "Bearer";

    /**
     * Access token expiry duration in milliseconds.
     * Allows the Angular HTTP interceptor to schedule proactive token refresh.
     */
    private long expiresIn;

    // =========================================================
    // User snapshot — for client-side session bootstrapping
    // =========================================================

    /**
     * The authenticated user's internal database ID.
     * Used to scope API calls (e.g., GET /api/voter/{id}/elections).
     */
    private Long userId;

    /**
     * The authenticated user's email (= Spring Security principal).
     * Displayed in the UI header / profile section.
     */
    private String email;

    /**
     * The user's first name — for personalised UI greetings.
     */
    private String prenom;

    /**
     * The user's last name.
     */
    private String nom;

    /**
     * The user's assigned role — drives Angular route guard decisions.
     * Values: ADMIN, ELECTEUR, OBSERVATEUR
     */
    private RoleUtilisateur role;

    /**
     * Whether the user account is currently active.
     * A disabled account will receive 401 on the next login attempt.
     */
    private boolean enabled;

    /**
     * For ELECTEUR role: whether Two-Factor Authentication is enabled.
     * If true, the Angular client must redirect to the /2fa/verify page
     * after a successful login before granting access to the voting booth.
     */
    private Boolean doubleFacteurActif;

    /**
     * For ELECTEUR role: whether the OTP has been verified in this session.
     * Used by the Angular route guard to conditionally block /vote routes.
     */
    private Boolean otpVerified;

    // =========================================================
    // Static factory methods for common cases
    // =========================================================

    /**
     * Builds a minimal AuthResponse suitable for a standard (non-2FA) login flow.
     *
     * @param accessToken  the signed JWT
     * @param refreshToken the refresh token
     * @param expiresIn    access token TTL in milliseconds
     * @param userId       the authenticated user's ID
     * @param email        the authenticated user's email
     * @param prenom       first name
     * @param nom          last name
     * @param role         assigned role
     * @return fully populated AuthResponse
     */
    public static AuthResponse of(
            String accessToken,
            String refreshToken,
            long expiresIn,
            Long userId,
            String email,
            String prenom,
            String nom,
            RoleUtilisateur role) {

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .userId(userId)
                .email(email)
                .prenom(prenom)
                .nom(nom)
                .role(role)
                .enabled(true)
                .doubleFacteurActif(null)
                .otpVerified(null)
                .build();
    }

    /**
     * Builds an AuthResponse for an ELECTEUR voter, including 2FA state fields.
     *
     * @param accessToken        the signed JWT
     * @param refreshToken       the refresh token
     * @param expiresIn          access token TTL in milliseconds
     * @param userId             the authenticated voter's ID
     * @param email              the authenticated voter's email
     * @param prenom             first name
     * @param nom                last name
     * @param doubleFacteurActif whether 2FA is enabled for this voter
     * @param otpVerified        whether OTP was already verified in this session
     * @return fully populated AuthResponse with 2FA fields
     */
    public static AuthResponse forElecteur(
            String accessToken,
            String refreshToken,
            long expiresIn,
            Long userId,
            String email,
            String prenom,
            String nom,
            boolean doubleFacteurActif,
            boolean otpVerified) {

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .userId(userId)
                .email(email)
                .prenom(prenom)
                .nom(nom)
                .role(RoleUtilisateur.ELECTEUR)
                .enabled(true)
                .doubleFacteurActif(doubleFacteurActif)
                .otpVerified(otpVerified)
                .build();
    }
}
