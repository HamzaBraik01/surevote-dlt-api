package ma.youcode.surevote.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Payload de vérification OTP 2FA")
public record OtpVerificationRequest(
    @Schema(description = "Code OTP à 6 chiffres", example = "123456")
    @NotBlank(message = "Le code OTP est obligatoire")
    @Pattern(regexp = "^[0-9]{6}$", message = "Le code OTP doit être composé de 6 chiffres")
    String otpCode
) {}
