package ma.youcode.surevote.exception;

import jakarta.servlet.http.HttpServletRequest;
import ma.youcode.surevote.domain.enums.StatutElection;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks private GlobalExceptionHandler handler;
    @Mock private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn("/api/test");
    }

    @Test
    void handleResourceNotFound_shouldReturn404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Election", 1L);
        ResponseEntity<Map<String, Object>> response = handler.handleResourceNotFound(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("error", "Resource Not Found");
    }

    @Test
    void handleAlreadyVoted_shouldReturn409() {
        AlreadyVotedException ex = new AlreadyVotedException(1L);
        ResponseEntity<Map<String, Object>> response = handler.handleAlreadyVoted(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("error", "Duplicate Vote");
    }

    @Test
    void handleElectionNotOpen_shouldReturn422() {
        ElectionNotOpenException ex = new ElectionNotOpenException(1L, StatutElection.BROUILLON);
        ResponseEntity<Map<String, Object>> response = handler.handleElectionNotOpen(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("error", "Election Not Open");
    }

    @Test
    void handleInvalidOtp_shouldReturn401() {
        InvalidOtpException ex = new InvalidOtpException("Invalid");
        ResponseEntity<Map<String, Object>> response = handler.handleInvalidOtp(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "Invalid OTP");
    }

    @Test
    void handleDuplicateResource_shouldReturn409() {
        DuplicateResourceException ex = new DuplicateResourceException("Email already exists");
        ResponseEntity<Map<String, Object>> response = handler.handleDuplicateResource(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("error", "Duplicate Resource");
    }

    @Test
    void handleInvalidElectionState_shouldReturn400() {
        InvalidElectionStateException ex = new InvalidElectionStateException("Invalid transition");
        ResponseEntity<Map<String, Object>> response = handler.handleInvalidElectionState(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Invalid Election State");
    }

    @Test
    void handleVoterNotEligible_shouldReturn403() {
        VoterNotEligibleException ex = new VoterNotEligibleException("Not eligible");
        ResponseEntity<Map<String, Object>> response = handler.handleVoterNotEligible(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("error", "Voter Not Eligible");
    }

    @Test
    void handleTwoFactorRequired_shouldReturn403() {
        TwoFactorRequiredException ex = new TwoFactorRequiredException();
        ResponseEntity<Map<String, Object>> response = handler.handleTwoFactorRequired(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("error", "Two-Factor Authentication Required");
    }

    @Test
    void handleResultsNotAvailable_shouldReturn422() {
        ResultsNotAvailableException ex = new ResultsNotAvailableException(1L);
        ResponseEntity<Map<String, Object>> response = handler.handleResultsNotAvailable(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("error", "Results Not Available");
    }

    @Test
    void handleBadCredentials_shouldReturn401() {
        BadCredentialsException ex = new BadCredentialsException("Bad creds");
        ResponseEntity<Map<String, Object>> response = handler.handleBadCredentials(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "Authentication Failed");
    }

    @Test
    void handleDisabledAccount_shouldReturn401() {
        DisabledException ex = new DisabledException("Disabled");
        ResponseEntity<Map<String, Object>> response = handler.handleDisabledAccount(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "Account Disabled");
    }

    @Test
    void handleLockedAccount_shouldReturn401() {
        LockedException ex = new LockedException("Locked");
        ResponseEntity<Map<String, Object>> response = handler.handleLockedAccount(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "Account Locked");
    }

    @Test
    void handleAccessDenied_shouldReturn403() {
        AccessDeniedException ex = new AccessDeniedException("Forbidden");
        ResponseEntity<Map<String, Object>> response = handler.handleAccessDenied(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("error", "Access Denied");
    }

    @Test
    void handleJwtAuthentication_shouldReturn401() {
        JwtAuthenticationException ex = new JwtAuthenticationException("JWT expired");
        ResponseEntity<Map<String, Object>> response = handler.handleJwtAuthentication(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "JWT Authentication Failed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleValidationErrors_shouldReturn400WithFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("obj", "email", "must not be blank");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<Map<String, Object>> response = handler.handleValidationErrors(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("fieldErrors");
        Map<String, String> fieldErrors = (Map<String, String>) response.getBody().get("fieldErrors");
        assertThat(fieldErrors).containsEntry("email", "must not be blank");
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleConstraintViolation_shouldReturn400WithViolations() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("id");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must be positive");
        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<Map<String, Object>> response = handler.handleConstraintViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("violations");
    }

    @Test
    void handleTypeMismatch_shouldReturn400() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("id");
        when(ex.getRequiredType()).thenReturn((Class) Long.class);

        ResponseEntity<Map<String, Object>> response = handler.handleTypeMismatch(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Type Mismatch");
    }

    @Test
    void handleTypeMismatch_nullRequiredType() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("id");
        when(ex.getRequiredType()).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = handler.handleTypeMismatch(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleIllegalState_shouldReturn400() {
        IllegalStateException ex = new IllegalStateException("Invalid state");
        ResponseEntity<Map<String, Object>> response = handler.handleIllegalState(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Illegal State");
    }

    @Test
    void handleIllegalArgument_shouldReturn400() {
        IllegalArgumentException ex = new IllegalArgumentException("Bad argument");
        ResponseEntity<Map<String, Object>> response = handler.handleIllegalArgument(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Invalid Argument");
    }

    @Test
    void handleGenericException_shouldReturn500() {
        Exception ex = new RuntimeException("Something went wrong");
        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "Internal Server Error");
    }

    // Verify response body structure
    @Test
    void allResponses_shouldContainStandardFields() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Test", 1L);
        ResponseEntity<Map<String, Object>> response = handler.handleResourceNotFound(ex, request);
        Map<String, Object> body = response.getBody();
        assertThat(body).containsKeys("timestamp", "status", "error", "message", "path");
        assertThat(body.get("path")).isEqualTo("/api/test");
        assertThat(body.get("status")).isEqualTo(404);
    }
}
