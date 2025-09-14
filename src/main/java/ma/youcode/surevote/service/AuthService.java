package ma.youcode.surevote.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.annotation.Auditable;
import ma.youcode.surevote.domain.entity.Administrateur;
import ma.youcode.surevote.domain.entity.Electeur;
import ma.youcode.surevote.domain.entity.Observateur;
import ma.youcode.surevote.domain.entity.Utilisateur;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import ma.youcode.surevote.dto.request.LoginRequest;
import ma.youcode.surevote.dto.request.OtpVerificationRequest;
import ma.youcode.surevote.dto.request.RegisterRequest;
import ma.youcode.surevote.dto.response.AuthResponse;
import ma.youcode.surevote.exception.DuplicateResourceException;
import ma.youcode.surevote.exception.InvalidOtpException;
import ma.youcode.surevote.exception.JwtAuthenticationException;
import ma.youcode.surevote.exception.ResourceNotFoundException;
import ma.youcode.surevote.repository.UtilisateurRepository;
import ma.youcode.surevote.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Core authentication service for the SUREVOTE platform.
 *
 * Responsibilities:
 *  - User self-registration (all roles, but ADMIN can also register via admin panel)
 *  - Stateless JWT-based login
 *  - Refresh token issuance
 *  - Two-Factor Authentication (2FA) OTP verification for ELECTEUR role
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final OtpService otpService;
    private final TokenBlacklistService tokenBlacklistService;

    // =========================================================
    // Registration
    // =========================================================

    /**
     * Registers a new user on the SUREVOTE platform.
     *
     * The role defaults to ELECTEUR if not specified.
     * Passwords are hashed with BCrypt (cost factor 12) before storage.
     * Duplicate email and CIN are rejected with a 409 Conflict.
     *
     * @param request the registration form data
     * @return AuthResponse containing the JWT tokens and user profile snapshot
     */
    @Transactional
    @Auditable(actionType = "USER_REGISTERED", description = "Nouvel utilisateur enregistré")
    public AuthResponse register(RegisterRequest request) {
        // --- Validate uniqueness ---
        if (utilisateurRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException(
                    "Un compte avec l'adresse email '" + request.getEmail() + "' existe déjà."
            );
        }
        if (utilisateurRepository.existsByCin(request.getCin())) {
            throw new DuplicateResourceException(
                    "Un compte avec le CIN '" + request.getCin() + "' existe déjà."
            );
        }

        // --- Validate password confirmation ---
        if (!request.getMotDePasse().equals(request.getConfirmationMotDePasse())) {
            throw new IllegalArgumentException(
                    "Le mot de passe et sa confirmation ne correspondent pas."
            );
        }

        // --- Determine role (always ELECTEUR for self-registration) ---
        // Self-registration is ALWAYS ELECTEUR regardless of what's in the request.
        // Only an authenticated ADMIN can create ADMIN or OBSERVATEUR accounts
        // via POST /api/admin/users (AdminUserController).
        RoleUtilisateur role = RoleUtilisateur.ELECTEUR;

        // --- Hash password ---
        String hashedPassword = passwordEncoder.encode(request.getMotDePasse());

        // --- Build the correct subtype ---
        Utilisateur utilisateur = buildUtilisateur(request, role, hashedPassword);

        // --- Persist ---
        Utilisateur saved = utilisateurRepository.save(utilisateur);
        log.info("New user registered: id={}, email={}, role={}", saved.getId(), saved.getEmail(), role);

        // --- Issue tokens ---
        String accessToken  = jwtTokenProvider.generateAccessToken(saved);
        String refreshToken = jwtTokenProvider.generateRefreshToken(saved);

        return buildAuthResponse(saved, accessToken, refreshToken);
    }

    // =========================================================
    // Login
    // =========================================================

    /**
     * Authenticates a user with their email and password.
     *
     * Steps:
     *  1. Delegate to Spring Security's AuthenticationManager (validates credentials + account status).
     *  2. Issue a signed JWT access token and a refresh token.
     *  3. If the user is an ELECTEUR with 2FA enabled, trigger OTP dispatch.
     *
     * The login method itself is monitored by AuditAspect which logs
     * LOGIN_SUCCESS and LOGIN_FAILURE events automatically.
     *
     * @param request login credentials
     * @return AuthResponse with tokens and user profile
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Authenticate — throws BadCredentialsException / DisabledException on failure
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.motDePasse())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        Utilisateur utilisateur = (Utilisateur) authentication.getPrincipal();

        // Issue tokens
        String accessToken  = jwtTokenProvider.generateAccessToken(utilisateur);
        String refreshToken = jwtTokenProvider.generateRefreshToken(utilisateur);

        // If ELECTEUR with 2FA enabled → reset otpVerified and send OTP
        if (utilisateur instanceof Electeur electeur && electeur.isDoubleFacteurActif()) {
            electeur.setOtpVerified(false);
            utilisateurRepository.save(electeur);  // persist the reset
            otpService.generateAndSendOtp(electeur);
            log.info("2FA OTP sent to voter: {}", electeur.getEmail());
        }

        log.info("User logged in: id={}, email={}, role={}", utilisateur.getId(), utilisateur.getEmail(), utilisateur.getRole());
        return buildAuthResponse(utilisateur, accessToken, refreshToken);
    }

    // =========================================================
    // Token Refresh
    // =========================================================

    /**
     * Validates a refresh token and issues a new access token.
     *
     * The refresh token is validated for:
     *  - Valid signature (same secret key)
     *  - Not expired
     *  - The referenced user still exists and is enabled
     *
     * @param refreshToken the refresh token string (without "Bearer " prefix)
     * @return a new AuthResponse with a fresh access token
     */
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new JwtAuthenticationException(
                    "Le token de rafraîchissement est invalide ou expiré. Veuillez vous reconnecter."
            );
        }

        String email = jwtTokenProvider.getEmailFromToken(refreshToken);
        Utilisateur utilisateur = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Utilisateur introuvable pour le token fourni."
                ));

        if (!utilisateur.isEnabled()) {
            throw new JwtAuthenticationException(
                    "Ce compte a été désactivé. Impossible de rafraîchir le token."
            );
        }

        String newAccessToken  = jwtTokenProvider.generateAccessToken(utilisateur);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(utilisateur);

        log.debug("Token refreshed for: {}", email);
        return buildAuthResponse(utilisateur, newAccessToken, newRefreshToken);
    }

    // =========================================================
    // Two-Factor Authentication (2FA)
    // =========================================================

    /**
     * Verifies the OTP code submitted by an ELECTEUR voter.
     *
     * Validates:
     *  - The currently authenticated user is an ELECTEUR
     *  - The OTP code matches the stored code
     *  - The OTP has not expired (default: 5 minutes)
     *
     * Upon successful verification, the voter's otpVerified flag is set to true,
     * unlocking access to the voting booth endpoints.
     *
     * @param request  the OTP code submitted by the voter
     * @param electeur the currently authenticated voter entity
     * @return updated AuthResponse confirming OTP verification
     */
    @Transactional
    @Auditable(actionType = "OTP_VERIFIED", description = "Vérification 2FA réussie")
    public AuthResponse verifyOtp(OtpVerificationRequest request, Electeur electeur) {
        otpService.verifyOtp(electeur, request.otpCode());

        // Re-issue tokens (with updated OTP state reflected in next requests)
        String accessToken  = jwtTokenProvider.generateAccessToken(electeur);
        String refreshToken = jwtTokenProvider.generateRefreshToken(electeur);

        log.info("2FA OTP verified successfully for voter: {}", electeur.getEmail());
        return buildAuthResponse(electeur, accessToken, refreshToken);
    }

    @Transactional
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtAuthenticationException("Token manquant pour la déconnexion.");
        }
        if (!jwtTokenProvider.validateToken(token)) {
            throw new JwtAuthenticationException("Token invalide ou expiré.");
        }
        Instant expiresAt = jwtTokenProvider.getExpirationDateFromToken(token).toInstant();
        tokenBlacklistService.blacklist(token, expiresAt);
        SecurityContextHolder.clearContext();
    }

    /**
     * Re-sends a new OTP to the voter's registered email address.
     * The previous OTP is invalidated and a fresh one is generated.
     *
     * @param electeur the currently authenticated voter
     */
    @Transactional
    @Auditable(actionType = "OTP_RESENT", description = "OTP renvoyé à l'électeur")
    public void resendOtp(Electeur electeur) {
        otpService.generateAndSendOtp(electeur);
        log.info("OTP resent to voter: {}", electeur.getEmail());
    }

    // =========================================================
    // Private helpers
    // =========================================================

    /**
     * Constructs the correct Utilisateur subtype based on the requested role.
     */
    private Utilisateur buildUtilisateur(RegisterRequest req, RoleUtilisateur role, String hashedPassword) {
        return switch (role) {
            case ADMIN -> {
                Administrateur admin = new Administrateur();
                populateBaseFields(admin, req, role, hashedPassword);
                admin.setDepartement(req.getDepartement());
                yield admin;
            }
            case OBSERVATEUR -> {
                Observateur obs = new Observateur();
                populateBaseFields(obs, req, role, hashedPassword);
                obs.setOrganisme(req.getOrganisme());
                yield obs;
            }
            default -> {  // ELECTEUR
                Electeur electeur = new Electeur();
                populateBaseFields(electeur, req, role, hashedPassword);
                electeur.setTelephone(req.getTelephone());
                electeur.setDoubleFacteurActif(req.isDoubleFacteurActif());
                yield electeur;
            }
        };
    }

    /**
     * Sets the shared base fields on a Utilisateur instance.
     */
    private void populateBaseFields(Utilisateur u, RegisterRequest req,
                                     RoleUtilisateur role, String hashedPassword) {
        u.setCin(req.getCin().toUpperCase().trim());
        u.setNom(req.getNom().trim());
        u.setPrenom(req.getPrenom().trim());
        u.setEmail(req.getEmail().toLowerCase().trim());
        u.setMotDePasse(hashedPassword);
        u.setRole(role);
        u.setEnabled(true);
    }

    /**
     * Builds the appropriate AuthResponse based on the user's type and role.
     */
    private AuthResponse buildAuthResponse(Utilisateur utilisateur, String accessToken, String refreshToken) {
        long expiresIn = jwtTokenProvider.getExpirationMs();

        if (utilisateur instanceof Electeur electeur) {
            return AuthResponse.forElecteur(
                    accessToken,
                    refreshToken,
                    expiresIn,
                    electeur.getId(),
                    electeur.getEmail(),
                    electeur.getPrenom(),
                    electeur.getNom(),
                    electeur.isDoubleFacteurActif(),
                    electeur.isOtpVerified()
            );
        }

        return AuthResponse.of(
                accessToken,
                refreshToken,
                expiresIn,
                utilisateur.getId(),
                utilisateur.getEmail(),
                utilisateur.getPrenom(),
                utilisateur.getNom(),
                utilisateur.getRole()
        );
    }
}
