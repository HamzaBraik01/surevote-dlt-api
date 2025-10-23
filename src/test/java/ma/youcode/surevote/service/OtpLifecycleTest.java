package ma.youcode.surevote.service;

import ma.youcode.surevote.domain.entity.Electeur;
import ma.youcode.surevote.exception.InvalidOtpException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Electeur OTP lifecycle (no Spring context needed).
 */
class OtpLifecycleTest {

    @Test
    void assignOtp_setsCodeAndExpiry() {
        Electeur electeur = new Electeur();
        electeur.assignOtp("hashed-code", 5);

        assertThat(electeur.getOtpCode()).isEqualTo("hashed-code");
        assertThat(electeur.getOtpExpiry()).isAfter(LocalDateTime.now());
        assertThat(electeur.isOtpVerified()).isFalse();
    }

    @Test
    void clearOtp_removesCodeAndSetsVerified() {
        Electeur electeur = new Electeur();
        electeur.assignOtp("hashed-code", 5);
        electeur.clearOtp();

        assertThat(electeur.getOtpCode()).isNull();
        assertThat(electeur.getOtpExpiry()).isNull();
        assertThat(electeur.isOtpVerified()).isTrue();
    }

    @Test
    void isOtpValid_returnsFalse_whenExpired() {
        Electeur electeur = new Electeur();
        electeur.setOtpCode("code");
        electeur.setOtpExpiry(LocalDateTime.now().minusMinutes(1)); // already expired

        assertThat(electeur.isOtpValid()).isFalse();
    }

    @Test
    void isOtpValid_returnsTrue_whenNotExpired() {
        Electeur electeur = new Electeur();
        electeur.assignOtp("code", 5);

        assertThat(electeur.isOtpValid()).isTrue();
    }

    @Test
    void isOtpValid_returnsFalse_whenNoOtp() {
        Electeur electeur = new Electeur();
        assertThat(electeur.isOtpValid()).isFalse();
    }
}
