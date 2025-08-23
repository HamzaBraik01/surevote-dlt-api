package ma.youcode.surevote.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a registered voter (ELECTEUR) in the SUREVOTE platform.
 * Extends Utilisateur with voting-specific fields including 2FA configuration
 * and OTP management for secure ballot access.
 */
@Entity
@DiscriminatorValue("ELECTEUR")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Electeur extends Utilisateur {

    /**
     * Phone number for SMS-based OTP delivery.
     */
    @Column(name = "telephone")
    private String telephone;

    /**
     * Flag controlling whether Two-Factor Authentication is required
     * before the voter can access the voting booth.
     */
    @Column(name = "double_facteur_actif")
    private boolean doubleFacteurActif = true;

    /**
     * Currently active OTP code (hashed or plain, short-lived).
     * Cleared after successful verification or expiry.
     */
    @Column(name = "otp_code")
    private String otpCode;

    /**
     * Expiry timestamp of the current OTP.
     * OTP is invalid if current time exceeds this value.
     */
    @Column(name = "otp_expiry")
    private LocalDateTime otpExpiry;

    /**
     * Flag indicating the voter has completed 2FA for the current session.
     * Reset on logout or new login.
     */
    @Column(name = "otp_verified")
    private boolean otpVerified = false;

    /**
     * The electoral college this voter belongs to.
     * Restricts access to elections assigned to specific colleges.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "college_electoral_id")
    private CollegeElectoral collegeElectoral;

    /**
     * Emargement records proving participation (without revealing vote choice).
     * Each entry corresponds to an election the voter has participated in.
     */
    @OneToMany(mappedBy = "electeur", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Emargement> emargements = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Business methods
    // -----------------------------------------------------------------------

    /**
     * Assigns a new OTP code with a calculated expiry window.
     *
     * @param code          the generated OTP string
     * @param expiryMinutes duration in minutes before the OTP expires
     */
    public void assignOtp(String code, int expiryMinutes) {
        this.otpCode = code;
        this.otpExpiry = LocalDateTime.now().plusMinutes(expiryMinutes);
        this.otpVerified = false;
    }

    /**
     * Clears the OTP fields after successful verification or manual invalidation.
     */
    public void clearOtp() {
        this.otpCode = null;
        this.otpExpiry = null;
        this.otpVerified = true;
    }

    /**
     * Checks whether the stored OTP is still within its validity window.
     *
     * @return true if the OTP exists and has not yet expired
     */
    public boolean isOtpValid() {
        return otpCode != null
                && otpExpiry != null
                && LocalDateTime.now().isBefore(otpExpiry);
    }

    /**
     * Checks whether the voter has already voted in a given election
     * by scanning their emargement list.
     *
     * @param electionId the election to check
     * @return true if an emargement entry exists for that election
     */
    public boolean hasVotedIn(Long electionId) {
        return emargements.stream()
                .anyMatch(e -> e.getElection().getId().equals(electionId));
    }
}
