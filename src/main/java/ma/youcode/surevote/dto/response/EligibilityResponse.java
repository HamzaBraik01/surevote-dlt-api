package ma.youcode.surevote.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Result of a voter eligibility check for a specific election")
public class EligibilityResponse {

    @Schema(description = "The election ID that was checked", example = "1")
    private Long electionId;

    @Schema(description = "Whether the voter can proceed to cast their ballot", example = "true")
    private boolean eligible;

    @Schema(description = "Whether the voter has already voted in this election", example = "false")
    private boolean alreadyVoted;

    @Schema(description = "Whether OTP 2FA verification is required before voting", example = "false")
    private boolean requiresOtp;

    @Schema(description = "Human-readable explanation of the eligibility status")
    private String message;

    @Schema(description = "The voter's existing receipt UUID if they already voted — null otherwise",
            example = "550e8400-e29b-41d4-a716-446655440000")
    private String existingReceipt;
}
