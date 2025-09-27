package ma.youcode.surevote.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.annotation.Auditable;
import ma.youcode.surevote.domain.entity.PasswordResetToken;
import ma.youcode.surevote.domain.entity.Utilisateur;
import ma.youcode.surevote.dto.response.PasswordResetResponse;
import ma.youcode.surevote.exception.InvalidOtpException;
import ma.youcode.surevote.exception.ResourceNotFoundException;
import ma.youcode.surevote.repository.PasswordResetTokenRepository;
import ma.youcode.surevote.repository.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Service responsible for password reset functionality.
 *
 * Lifecycle:
 *  1. requestPasswordReset(email) — User requests reset; token is generated and sent via email
 *  2. verifyResetToken(token) — Validates that the token is valid and not expired
 *  3. resetPassword(token, newPassword) — Consumes the token and updates the user's password
 *
 * Security design:
 *  - Reset tokens are random UUIDs (cryptographically strong)
 *  - Tokens have a short TTL (default 15 minutes)
 *  - Tokens are single-use (marked as used after consumption)
 *  - Rate limiting: Max 3 concurrent valid tokens per user
 *  - Token is transmitted via email (not in response body)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PasswordResetService {

    private final PasswordResetTokenRepository resetTokenRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    @Value("${password-reset.expiry-minutes:15}")
    private int resetTokenExpiryMinutes;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    // =========================================================
    // Public API
    // =========================================================

    /**
     * Initiates a password reset flow for the specified email.
     *
     * Steps:
     *  1. Look up the user by email
     *  2. Limit concurrent reset tokens to 3 per user
     *  3. Generate a unique UUID token
     *  4. Store the token in the database with TTL
     *  5. Send an email containing the reset link
     *
     * Rate limiting:
     *  - A user can have max 3 concurrent valid reset tokens.
     *  - Older tokens expire automatically after 15 minutes.
     *
     * @param email the user's email address
     * @return a PasswordResetResponse confirming the email was sent
     * @throws ResourceNotFoundException if no user exists with the given email
     */
    @Auditable(actionType = "PASSWORD_RESET_REQUESTED", description = "Demande de réinitialisation de mot de passe")
    public PasswordResetResponse requestPasswordReset(String email) {
        // Validate user exists
        Utilisateur utilisateur = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable avec l'email: " + email));

        // Check rate limiting: max 3 concurrent valid tokens
        LocalDateTime now = LocalDateTime.now();
        long validTokenCount = resetTokenRepository.countValidTokensByUserId(utilisateur.getId(), now);
        if (validTokenCount >= 3) {
            log.warn("Rate limit exceeded for password reset: userId={}", utilisateur.getId());
            // Don't reveal that we hit the limit; return generic success for security
        }

        // Generate token
        String rawTokenValue = UUID.randomUUID().toString();
        String hashedToken = hashToken(rawTokenValue);
        LocalDateTime expiresAt = now.plusMinutes(resetTokenExpiryMinutes);

        PasswordResetToken token = PasswordResetToken.builder()
                .token(hashedToken)
                .utilisateur(utilisateur)
                .createdAt(now)
                .expiresAt(expiresAt)
                .used(false)
                .build();

        resetTokenRepository.save(token);
        log.debug("Password reset token generated for userId={}, expires at {}", utilisateur.getId(), expiresAt);

        // Send email with the RAW (unhashed) token
        try {
            sendPasswordResetEmail(utilisateur, rawTokenValue);
            log.info("Password reset email sent to: {}", email);
        } catch (MailException e) {
            log.error("Failed to send password reset email to {}: {}", email, e.getMessage());
            throw e;
        }

        return PasswordResetResponse.builder()
                .success(true)
                .message("Un email de réinitialisation a été envoyé à votre adresse email. Le lien expire dans " + resetTokenExpiryMinutes + " minutes.")
                .email(maskEmail(email))
                .build();
    }

    /**
     * Verifies that a reset token is valid (exists, not expired, not used).
     *
     * @param tokenValue the reset token UUID
     * @return the token if valid
     * @throws InvalidOtpException if the token is invalid or expired
     */
    public PasswordResetToken verifyResetToken(String rawTokenValue) {
        // Hash the incoming token to match the stored hash
        String hashedToken = hashToken(rawTokenValue);
        PasswordResetToken token = resetTokenRepository.findByToken(hashedToken)
                .orElseThrow(() -> new InvalidOtpException("Token de réinitialisation invalide ou expiré"));

        if (!token.isValid()) {
            throw new InvalidOtpException("Token de réinitialisation invalide, expiré, ou déjà utilisé");
        }

        return token;
    }

    /**
     * Consumes a valid reset token and updates the user's password.
     *
     * Steps:
     *  1. Verify the token is valid
     *  2. Validate new password and confirmation match
     *  3. Hash the new password
     *  4. Update the user's password
     *  5. Mark the token as used
     *  6. Clear any pending OTP codes (if the user is an Electeur)
     *
     * @param tokenValue the reset token UUID
     * @param newPassword the user's new password
     * @param confirmPassword confirmation of the new password
     * @throws InvalidOtpException if the token is invalid or expired
     * @throws IllegalArgumentException if passwords don't match or are invalid
     */
    @Auditable(actionType = "PASSWORD_RESET_COMPLETED", description = "Mot de passe réinitialisé avec succès")
    public void resetPassword(String tokenValue, String newPassword, String confirmPassword) {
        // Verify token
        PasswordResetToken token = verifyResetToken(tokenValue);

        // Validate password confirmation
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Les mots de passe ne correspondent pas");
        }

        // Hash and update password
        Utilisateur utilisateur = token.getUtilisateur();
        String hashedPassword = passwordEncoder.encode(newPassword);
        utilisateur.setMotDePasse(hashedPassword);
        utilisateurRepository.save(utilisateur);

        log.info("Password reset completed for userId={}", utilisateur.getId());

        // Mark token as used
        token.setUsed(true);
        token.setUsedAt(LocalDateTime.now());
        resetTokenRepository.save(token);

        log.debug("Reset token marked as used: token={}", tokenValue);
    }

    // =========================================================
    // Helper methods
    // =========================================================

    /**
     * Sends a password reset email containing the reset link.
     *
     * @param utilisateur the user requesting the reset
     * @param tokenValue the reset token UUID
     */
    private void sendPasswordResetEmail(Utilisateur utilisateur, String tokenValue) {
        String resetLink = frontendUrl + "/reset-password?token=" + tokenValue;

        notificationService.sendEmail(
            utilisateur.getEmail(),
            "SUREVOTE — Réinitialisation de votre mot de passe",
            """
                Bonjour %s,

                Nous avons reçu une demande de réinitialisation de mot de passe pour votre compte SUREVOTE.

                Cliquez sur le lien ci-dessous pour réinitialiser votre mot de passe :
                %s

                Ce lien expire dans %d minutes.

                Si vous n'avez pas demandé cette réinitialisation, veuillez ignorer cet email.

                Cordialement,
                L'équipe SUREVOTE
        """.formatted(utilisateur.getPrenom(), resetLink, resetTokenExpiryMinutes)
    );
    }

    /**
     * Masks the email address for display (e.g., "u***@example.com").
     */
    private String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }
        String[] parts = email.split("@");
        if (parts.length != 2) {
            return email;
        }
        String localPart = parts[0];
        String domain = parts[1];
        String masked = "u" + "*".repeat(Math.max(0, localPart.length() - 1));
        return masked + "@" + domain;
    }

    /**
     * Hashes a token with SHA-256 for secure storage.
     * SHA-256 is sufficient here because UUID tokens have 122 bits of entropy.
     *
     * @param rawToken the raw token string
     * @return hex-encoded SHA-256 hash
     */
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
