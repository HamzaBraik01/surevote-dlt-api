package ma.youcode.surevote.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO returned to a voter upon successful ballot submission.
 *
 * Contains the cryptographic UUID receipt that allows the voter to:
 *   1. Independently verify their participation on the public dashboard.
 *   2. Confirm their vote was counted — without revealing their choice.
 *
 * SECURITY NOTE: This response NEVER includes:
 *   - The candidate the voter voted for
 *   - Any link between the receipt and the actual Vote record
 *   - Any voter identity information beyond what they already know
 *
 * The receipt UUID is stored in the Emargement table only.
 * The Vote table has no reference to this receipt or to the voter.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoteReceiptResponse {

    /**
     * The cryptographic UUID receipt generated upon successful vote submission.
     * Format: standard UUID v4 (e.g., "550e8400-e29b-41d4-a716-446655440000")
     *
     * This is the voter's proof of participation. They should store it safely
     * to use on the public verification dashboard after results are published.
     */
    private String recuCryptographique;

    /**
     * The ID of the election in which the vote was cast.
     * Allows the voter to identify which election this receipt belongs to.
     */
    private Long electionId;

    /**
     * The title of the election — for display purposes in the confirmation UI.
     */
    private String electionTitre;

    /**
     * Timestamp confirming when the participation was recorded.
     * This is the Emargement timestamp, NOT the Vote timestamp
     * (which is intentionally obscured to prevent temporal correlation).
     */
    private LocalDateTime dateParticipation;

    /**
     * Confirmation message to display in the UI upon successful vote.
     */
    @Builder.Default
    private String message = "Votre vote a été enregistré avec succès. Conservez ce reçu pour vérifier votre participation.";

    /**
     * Whether the vote was successfully recorded.
     * Always true in this response (error cases throw exceptions before reaching this point).
     */
    @Builder.Default
    private boolean success = true;

    /**
     * URL the voter can use to verify their receipt on the public dashboard.
     * Constructed as: /public/verify/{recuCryptographique}
     */
    private String verificationUrl;

    // -----------------------------------------------------------------------
    // Static factory method
    // -----------------------------------------------------------------------

    /**
     * Creates a fully populated VoteReceiptResponse after successful ballot submission.
     *
     * @param recuCryptographique the generated UUID receipt
     * @param electionId          the election ID
     * @param electionTitre       the election title
     * @param dateParticipation   the timestamp of emargement creation
     * @return a ready-to-return VoteReceiptResponse
     */
    public static VoteReceiptResponse of(
            String recuCryptographique,
            Long electionId,
            String electionTitre,
            LocalDateTime dateParticipation) {

        return VoteReceiptResponse.builder()
                .recuCryptographique(recuCryptographique)
                .electionId(electionId)
                .electionTitre(electionTitre)
                .dateParticipation(dateParticipation)
                .verificationUrl("/public/verify/" + recuCryptographique)
                .success(true)
                .message("Votre vote a été enregistré avec succès. Conservez ce reçu pour vérifier votre participation.")
                .build();
    }
}
