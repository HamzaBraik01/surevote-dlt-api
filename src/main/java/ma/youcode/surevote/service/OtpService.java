package ma.youcode.surevote.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.domain.entity.Electeur;
import ma.youcode.surevote.exception.InvalidOtpException;
import ma.youcode.surevote.exception.ResourceNotFoundException;
import ma.youcode.surevote.repository.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * Service responsible for Two-Factor Authentication (2FA) via email OTP.
 *
 * Lifecycle:
 *  1. generateAndSendOtp(electeur) — generates a 6-digit code, stores it hashed on the
 *     Electeur entity, persists, and sends an email with the code.
 *  2. verifyOtp(electeur, code)    — validates the provided code against the stored value
 *     and checks the expiry window. Clears the OTP on success.
 *
 * Security notes:
 *  - OTP is generated with SecureRandom (cryptographically strong PRNG).
 *  - OTP has a configurable short TTL (default 5 minutes).
 *  - Failed verification does NOT clear the OTP (allows retry until expiry).
 *  - Successful verification clears the OTP and sets otpVerified = true.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final NotificationService notificationService;
    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    @Value("${otp.length:6}")
    private int otpLength;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // =========================================================
    // Public API
    // =========================================================

    /**
     * Generates a new OTP for the given voter, persists it on their profile,
     * and dispatches a formatted email with the code and expiry information.
     *
     * @param electeur the authenticated voter requiring 2FA
     * @throws MailException if the email could not be sent (logged; does not rollback)
     */
    @Transactional
    public void generateAndSendOtp(Electeur electeur) {
        String otpCode = generateOtpCode();
        String hashedOtp = passwordEncoder.encode(otpCode);

        // Send email FIRST — if it fails, the DB is never updated (transaction rolls back)
        try {
            sendOtpEmail(electeur, otpCode);
            log.info("OTP email sent to: {}", electeur.getEmail());
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", electeur.getEmail(), e.getMessage());
            throw e;  // rollback — DB not updated
        }

        // Only persist after successful email delivery
        electeur.assignOtp(hashedOtp, otpExpiryMinutes);
        utilisateurRepository.save(electeur);
        log.debug("OTP persisted for voter id={}, expires in {} minutes", electeur.getId(), otpExpiryMinutes);
    }

    /**
     * Verifies the OTP code provided by the voter.
     * On success: clears the OTP fields and marks otpVerified = true.
     * On failure: throws InvalidOtpException (OTP remains valid for retry until expiry).
     *
     * @param electeur the voter attempting OTP verification
     * @param providedCode the 6-digit code entered by the user
     * @throws InvalidOtpException if the code is incorrect, expired, or no OTP is pending
     */
    @Transactional
    public void verifyOtp(Electeur electeur, String providedCode) {
        // Guard: no OTP has been requested
        if (electeur.getOtpCode() == null) {
            throw new InvalidOtpException(
                "Aucun code OTP en attente. Veuillez d'abord demander un nouveau code."
            );
        }

        // Guard: OTP has expired
        if (!electeur.isOtpValid()) {
            throw new InvalidOtpException(
                "Le code OTP a expiré. Veuillez demander un nouveau code."
            );
        }

        // Guard: code mismatch (compare plaintext input against stored BCrypt hash)
        if (!passwordEncoder.matches(providedCode.trim(), electeur.getOtpCode())) {
            log.warn("Invalid OTP attempt for voter id={}", electeur.getId());
            throw new InvalidOtpException(
                "Code OTP incorrect. Veuillez vérifier le code reçu par email."
            );
        }

        // Success — clear OTP and mark session as 2FA-verified
        electeur.clearOtp();
        utilisateurRepository.save(electeur);

        log.info("OTP verified successfully for voter id={}", electeur.getId());
    }

    /**
     * Resends a new OTP for the given voter email.
     * Useful when the original email was lost or the OTP expired.
     *
     * @param email the voter's registered email address
     * @throws ResourceNotFoundException if no voter exists with that email
     */
    @Transactional
    public void resendOtp(String email) {
        Electeur electeur = (Electeur) utilisateurRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Electeur introuvable avec l'email: " + email));

        generateAndSendOtp(electeur);
        log.info("OTP resent to: {}", email);
    }

    /**
     * Checks whether a voter has completed the 2FA step in their current session.
     *
     * @param electeur the voter to check
     * @return true if OTP has been verified, false otherwise
     */
    public boolean isOtpVerified(Electeur electeur) {
        return electeur.isOtpVerified();
    }

    /**
     * Forcefully invalidates any pending OTP for the voter (e.g., on logout).
     *
     * @param electeur the voter whose OTP should be cleared
     */
    @Transactional
    public void invalidateOtp(Electeur electeur) {
        if (electeur.getOtpCode() != null) {
            electeur.setOtpCode(null);
            electeur.setOtpExpiry(null);
            electeur.setOtpVerified(false);
            utilisateurRepository.save(electeur);
            log.debug("OTP invalidated for voter id={}", electeur.getId());
        }
    }

    // =========================================================
    // Private helpers
    // =========================================================

    /**
     * Generates a cryptographically secure numeric OTP of the configured length.
     *
     * @return a zero-padded numeric string of length {@code otpLength}
     */
    private String generateOtpCode() {
        int max = (int) Math.pow(10, otpLength);
        int code = SECURE_RANDOM.nextInt(max);
        // Zero-pad to ensure consistent length (e.g., 000042 for length 6)
        return String.format("%0" + otpLength + "d", code);
    }

    /**
     * Composes and sends the OTP email to the voter.
     *
     * @param electeur the target voter
     * @param otpCode  the generated OTP to include in the message
     */
    private void sendOtpEmail(Electeur electeur, String otpCode) {
        notificationService.sendEmail(
                electeur.getEmail(),
                "SUREVOTE — Votre code de vérification",
                buildEmailBody(electeur, otpCode)
        );
    }

    /**
     * Builds a formatted, human-readable OTP email body.
     *
     * @param electeur the voter receiving the email
     * @param otpCode  the 6-digit OTP code
     * @return formatted plain-text email body
     */
    private String buildEmailBody(Electeur electeur, String otpCode) {
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(otpExpiryMinutes);
        return String.format("""
            Bonjour %s %s,

            Vous tentez d'accéder au bureau de vote SUREVOTE.

            ─────────────────────────────
              VOTRE CODE DE VÉRIFICATION
            ─────────────────────────────
                        %s
            ─────────────────────────────

            Ce code est valide pendant %d minutes (jusqu'à %s).
            Ne le communiquez à personne.

            Si vous n'êtes pas à l'origine de cette demande, ignorez cet email
            et contactez immédiatement l'administrateur de la plateforme.

            Cordialement,
            L'équipe SUREVOTE
            """,
            electeur.getPrenom(),
            electeur.getNom().toUpperCase(),
            otpCode,
            otpExpiryMinutes,
            expiry.toString().replace("T", " ").substring(0, 19)
        );
    }
}
