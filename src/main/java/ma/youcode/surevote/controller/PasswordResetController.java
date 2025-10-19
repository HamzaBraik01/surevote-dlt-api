package ma.youcode.surevote.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.dto.request.PasswordResetConfirmRequest;
import ma.youcode.surevote.dto.request.PasswordResetRequest;
import ma.youcode.surevote.dto.response.PasswordResetResponse;
import ma.youcode.surevote.service.PasswordResetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for password reset operations.
 *
 * Public endpoints:
 *   POST /api/auth/password-reset/request  — Request a password reset token
 *   POST /api/auth/password-reset/confirm  — Confirm reset with token + new password
 */
@RestController
@RequestMapping("/api/auth/password-reset")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "Password Reset",
    description = "Endpoints for initiating and confirming password reset via email token"
)
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /**
     * Requests a password reset token to be sent to the user's registered email.
     *
     * A reset link (containing a random UUID token) will be emailed to the address.
     * The link is valid for 15 minutes.
     *
     * @param request the password reset request containing the email address
     * @return 200 OK with a confirmation message
     */
    @PostMapping("/request")
    @Operation(
        summary = "Request a password reset",
        description = """
            Initiates a password reset flow by sending a reset link to the user's email.

            **Rate limiting:** 3 requests per hour per email address.
            
            **Response:** Always returns 200 OK for security (does not reveal if email exists).
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reset email sent (or queued for retry)",
            content = @Content(schema = @Schema(implementation = PasswordResetResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation failed — invalid email format")
    })
    public ResponseEntity<PasswordResetResponse> requestReset(@Valid @RequestBody PasswordResetRequest request) {
        log.info("Password reset requested for email: {}", request.getEmail());
        PasswordResetResponse response = passwordResetService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(response);
    }

    /**
     * Confirms a password reset using the token from the email and sets a new password.
     *
     * Steps:
     *  1. Validates the reset token is valid and not expired
     *  2. Verifies the new password and confirmation match
     *  3. Updates the user's password to the hashed new value
     *  4. Marks the token as consumed (single-use)
     *
     * @param request the reset confirmation containing token + new password
     * @return 200 OK if password was successfully reset
     */
    @PostMapping("/confirm")
    @Operation(
        summary = "Confirm password reset with token",
        description = """
            Completes the password reset flow by validating the token and updating the user's password.

            **Validation:**
            - Token must be valid (not expired, not already used)
            - New password must meet complexity requirements
            - Passwords must match
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Password reset completed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data — passwords don't match"),
        @ApiResponse(responseCode = "401", description = "Invalid or expired reset token")
    })
    public ResponseEntity<String> confirmReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        log.info("Password reset confirmation attempt");
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword(), request.getConfirmPassword());
        return ResponseEntity.ok("Mot de passe réinitialisé avec succès. Vous pouvez maintenant vous connecter.");
    }
}
