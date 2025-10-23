package ma.youcode.surevote.exception;

import ma.youcode.surevote.domain.enums.StatutElection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionClassesTest {

    @Test
    void alreadyVotedException_containsElectionId() {
        AlreadyVotedException ex = new AlreadyVotedException(42L);
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).contains("42");
    }

    @Test
    void duplicateResourceException_containsMessage() {
        DuplicateResourceException ex = new DuplicateResourceException("Email already exists");
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("Email already exists");
    }

    @Test
    void electionNotOpenException_containsElectionId() {
        ElectionNotOpenException ex = new ElectionNotOpenException(5L, StatutElection.BROUILLON);
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).contains("5").contains("BROUILLON");
    }

    @Test
    void invalidElectionStateException_containsMessage() {
        InvalidElectionStateException ex = new InvalidElectionStateException("Cannot transition");
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("Cannot transition");
    }

    @Test
    void invalidOtpException_containsMessage() {
        InvalidOtpException ex = new InvalidOtpException("OTP expired");
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("OTP expired");
    }

    @Test
    void jwtAuthenticationException_containsMessage() {
        JwtAuthenticationException ex = new JwtAuthenticationException("Token expired");
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("Token expired");
    }

    @Test
    void resourceNotFoundException_containsDetails() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Election", 99L);
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).contains("Election").contains("99");
    }

    @Test
    void resultsNotAvailableException_containsElectionId() {
        ResultsNotAvailableException ex = new ResultsNotAvailableException(7L);
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).contains("7");
    }

    @Test
    void twoFactorRequiredException_containsDefaultMessage() {
        TwoFactorRequiredException ex = new TwoFactorRequiredException();
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).contains("deux facteurs");
    }

    @Test
    void voterNotEligibleException_containsMessage() {
        VoterNotEligibleException ex = new VoterNotEligibleException("Not in college");
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("Not in college");
    }
}
