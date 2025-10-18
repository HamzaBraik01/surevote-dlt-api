package ma.youcode.surevote.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.dto.request.VoteRequest;
import ma.youcode.surevote.dto.response.EligibilityResponse;
import ma.youcode.surevote.dto.response.VoteReceiptResponse;
import ma.youcode.surevote.dto.response.endpoint.vote.VoteIntegrityResponse;
import ma.youcode.surevote.dto.response.endpoint.vote.VotedElectionsResponse;
import ma.youcode.surevote.mapper.endpoint.VoteEndpointMapper;
import ma.youcode.surevote.config.RateLimitingConfig;
import ma.youcode.surevote.service.VoteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * REST controller exposing the core voting operations of the SUREVOTE platform.
 *
 * Base path: /api/vote
 * Required role: ELECTEUR (for ballot submission and eligibility checks)
 *
 * Endpoints:
 *   GET  /api/vote/eligibility/{electionId}  — Check voter eligibility before loading the ballot
 *   POST /api/vote/submit                    — Submit an anonymous ballot (double-barrier atomic transaction)
 *   GET  /api/vote/receipt/{uuid}            — Verify a cryptographic receipt (also accessible publicly)
 *   GET  /api/vote/my-votes                  — List all elections the authenticated voter has voted in
 *   GET  /api/vote/my-receipt/{electionId}   — Re-retrieve the voter's receipt for a specific election
 *
 * Security notes:
 *  - Ballot submission is restricted to ELECTEUR role exclusively.
 *  - The voter's identity is NEVER accepted from the request body.
 *    It is always resolved server-side from the JWT token (prevents identity spoofing).
 *  - The Vote table contains NO reference to any voter — all anonymity is enforced
 *    at the service layer via the double-barrier pattern.
 *  - SERIALIZABLE transaction isolation prevents race conditions on concurrent submissions.
 */
@RestController
@RequestMapping("/api/vote")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "Voting",
    description = """
        Core voting endpoints for registered voters (ELECTEUR role).

        **Double-barrier anonymity:** When a ballot is submitted, two atomic records are created:
        1. An **Emargement** record linking the voter's identity to the election (WHO voted).
        2. A **Vote** record linking only the candidate to the election (WHAT was voted).

        These two records share no join key. No SQL query can ever link a voter's identity
        to their ballot choice — even with full database access.
        """
)
@SecurityRequirement(name = "Bearer Authentication")
public class VoteController {

    private final VoteService voteService;
    private final VoteEndpointMapper voteEndpointMapper;
    private final RateLimitingConfig rateLimiting;

    // =========================================================
    // Eligibility Check
    // =========================================================

    /**
     * Checks whether the authenticated voter is eligible to vote in the specified election.
     *
     * The eligibility check evaluates:
     *  1. Is the election currently OUVERTE (active)?
     *  2. Has the voter already voted in this election?
     *  3. Does the voter belong to the required electoral college (if restricted)?
     *  4. Has the voter completed Two-Factor Authentication (if required)?
     *
     * This endpoint is designed to be called BEFORE loading the ballot page,
     * allowing the Angular client to:
     *  - Show a "you've already voted" message with the existing receipt.
     *  - Redirect to the /2fa/verify page if OTP verification is pending.
     *  - Display an "access denied" message if the voter is not eligible.
     *  - Render the ballot form if the voter is fully eligible.
     *
     * @param electionId the ID of the election to check eligibility for
     * @return 200 OK with an EligibilityResult map describing the voter's status
     */
    @GetMapping("/eligibility/{electionId}")
    @PreAuthorize("hasRole('ELECTEUR')")
    @Operation(
        summary = "Check voter eligibility for an election",
        description = """
            Evaluates whether the currently authenticated voter is eligible to cast
            a ballot in the specified election.

            **Response fields:**
            - `eligible` (boolean): whether the voter can proceed to vote
            - `alreadyVoted` (boolean): whether the voter has already voted
            - `requiresOtp` (boolean): whether 2FA OTP must be verified first
            - `message` (string): a human-readable description of the eligibility status
            - `existingReceipt` (string or null): the voter's receipt UUID if they already voted

            **Use this endpoint before displaying the ballot page** to correctly route
            the voter to the appropriate UI view.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Eligibility result returned"),
        @ApiResponse(responseCode = "401", description = "JWT token missing or invalid"),
        @ApiResponse(responseCode = "403", description = "Access denied — ELECTEUR role required"),
        @ApiResponse(responseCode = "404", description = "Election not found")
    })
    public ResponseEntity<EligibilityResponse> checkEligibility(
            @Parameter(description = "The ID of the election to check eligibility for", required = true)
            @PathVariable Long electionId) {

        log.debug("GET /api/vote/eligibility/{} — eligibility check requested", electionId);

        VoteService.EligibilityResult result = voteService.checkEligibility(electionId);
        return ResponseEntity.ok(voteEndpointMapper.toEligibilityResponse(electionId, result));
    }

    // =========================================================
    // Ballot Submission — Core Double-Barrier Operation
    // =========================================================

    /**
     * Submits an anonymous ballot for the authenticated voter in the specified election.
     *
     * This is the most security-critical endpoint on the platform.
     * It executes the "double-barrier" atomic transaction:
     *
     *   STEP 1 — Write EMARGEMENT:
     *     Records that this voter has participated in this election.
     *     Contains: electeur_id + election_id + UUID receipt + IP + timestamp
     *     Purpose: Prevents double voting. Issues cryptographic proof to the voter.
     *
     *   STEP 2 — Write VOTE (anonymous ballot):
     *     Records the ballot choice with ZERO voter identity.
     *     Contains: election_id + candidat_id + obfuscated timestamp
     *     Purpose: Tallied for results. NEVER linked back to any voter.
     *
     * Both steps execute in a single @Transactional(SERIALIZABLE) block.
     * Both succeed together or both roll back atomically on any failure.
     *
     * Pre-flight security checks (all must pass):
     *  1. Election exists and is currently OUVERTE.
     *  2. The voter account is active and has the ELECTEUR role.
     *  3. If 2FA is enabled, the voter has completed OTP verification.
     *  4. The voter is in the required electoral college (if restricted).
     *  5. The voter has NOT already voted in this election.
     *  6. The candidate exists and belongs to the specified election.
     *
     * Returns a UUID cryptographic receipt the voter can use to verify
     * their participation on the public dashboard.
     *
     * @param request the vote submission: electionId + candidatId
     * @return 200 OK with VoteReceiptResponse containing the UUID receipt
     */
    @PostMapping("/submit")
    @PreAuthorize("hasRole('ELECTEUR')")
    @Operation(
        summary = "Submit an anonymous ballot",
        description = """
            Submits a ballot for the currently authenticated voter.

            **Request body:**
            ```json
            {
              "electionId": 1,
              "candidatId": 3
            }
            ```

            **Security guarantees:**
            - The voter's identity is extracted from the JWT token — never from the request body.
            - The Vote record created has **NO foreign key to any user table**.
            - The ballot timestamp is randomly obfuscated (±30 seconds) to prevent temporal
              correlation attacks linking the Vote record to the Emargement record.
            - SERIALIZABLE isolation prevents race conditions on simultaneous submissions.

            **Response:** A `VoteReceiptResponse` containing:
            - `recuCryptographique`: UUID receipt (save this to verify participation later)
            - `electionId` and `electionTitre`: the election voted in
            - `dateParticipation`: timestamp of participation
            - `verificationUrl`: path to the public receipt verification page

            Returns **409 Conflict** if the voter has already voted.
            Returns **422 Unprocessable Entity** if the election is not currently open.
            Returns **403 Forbidden** if 2FA is required but not completed.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ballot submitted successfully — receipt returned",
            content = @Content(schema = @Schema(implementation = VoteReceiptResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation error — missing electionId or candidatId"),
        @ApiResponse(responseCode = "401", description = "JWT token missing or invalid"),
        @ApiResponse(responseCode = "403", description = "Access denied — ELECTEUR role required, or 2FA not completed, or voter not eligible for this election"),
        @ApiResponse(responseCode = "404", description = "Election or candidate not found"),
        @ApiResponse(responseCode = "409", description = "Voter has already voted in this election — receipt returned in error body"),
        @ApiResponse(responseCode = "422", description = "Election is not currently open for voting")
    })
    public ResponseEntity<VoteReceiptResponse> submitVote(
            @Valid @RequestBody VoteRequest request,
            HttpServletRequest httpRequest) {

        // SEC-6: Rate limiting on vote submission
        String ip = httpRequest.getRemoteAddr();
        if (!rateLimiting.voteSubmitBucket(ip).tryConsume(1)) {
            log.warn("Rate limit exceeded for vote submission from IP: {}", ip);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        log.info("POST /api/vote/submit — electionId={}, candidatId={}",
                request.getElectionId(), request.getCandidatId());

        VoteReceiptResponse receipt = voteService.submitVote(request);

        log.info("Vote submitted successfully — recu={}", receipt.getRecuCryptographique());
        return ResponseEntity.ok(receipt);
    }

    // =========================================================
    // Receipt Verification
    // =========================================================

    /**
     * Verifies a cryptographic receipt UUID on the public participation dashboard.
     *
     * Allows any user (authenticated or not) to confirm that a given UUID receipt
     * corresponds to a recorded participation in SUREVOTE.
     *
     * This endpoint ONLY confirms:
     *  - That the receipt UUID exists in the Emargement table.
     *  - Which election the participation was recorded for (by title).
     *  - The timestamp of participation.
     *
     * This endpoint NEVER reveals:
     *  - The voter's identity.
     *  - The candidate they voted for.
     *  - Any link between the Emargement and Vote tables.
     *
     * @param uuid the UUID receipt string to verify
     * @return 200 OK with participation confirmation, or 404 if receipt not found
     */
    // FIX-1: Duplicate receipt endpoint removed.
    // Use GET /public/verify/{uuid} (PublicElectionController) for public receipt verification.

    // =========================================================
    // Voter History
    // =========================================================

    /**
     * Returns the list of election IDs in which the authenticated voter has already voted.
     *
     * Used by the Angular voter dashboard to:
     *  - Mark elections as "voted" in the election list view.
     *  - Disable the "Vote" button for elections the voter has participated in.
     *  - Display the voter's participation history.
     *
     * @return 200 OK with a list of election IDs the voter has voted in
     */
    @GetMapping("/my-votes")
    @PreAuthorize("hasRole('ELECTEUR')")
    @Operation(
        summary = "Get all elections the authenticated voter has voted in",
        description = """
            Returns the list of election IDs in which the currently authenticated voter
            has already submitted a ballot.

            Used by the Angular client to visually mark elections as "voted" in the list view
            and to prevent re-submission attempts on the client side.

            Note: Server-side duplicate prevention is enforced independently of this check.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of voted election IDs returned"),
        @ApiResponse(responseCode = "401", description = "JWT token missing or invalid"),
        @ApiResponse(responseCode = "403", description = "Access denied — ELECTEUR role required")
    })
    public ResponseEntity<VotedElectionsResponse> getMyVotedElections() {
        log.debug("GET /api/vote/my-votes — fetching voted elections for current voter");

        List<Long> votedElectionIds = voteService.getVotedElectionIds();
        return ResponseEntity.ok(voteEndpointMapper.toVotedElectionsResponse(votedElectionIds));
    }

    /**
     * Re-retrieves the cryptographic receipt for the authenticated voter in a specific election.
     *
     * Useful when the voter has lost or forgotten their original receipt after voting.
     * Returns the same UUID receipt that was generated and returned at vote submission time.
     *
     * Note: The receipt does NOT reveal the voter's ballot choice.
     * It only confirms participation in the specified election.
     *
     * @param electionId the ID of the election to retrieve the receipt for
     * @return 200 OK with the VoteReceiptResponse, or 404 if the voter hasn't voted in that election
     */
    @GetMapping("/my-receipt/{electionId}")
    @PreAuthorize("hasRole('ELECTEUR')")
    @Operation(
        summary = "Re-retrieve the voter's receipt for a specific election",
        description = """
            Returns the cryptographic UUID receipt for the currently authenticated voter
            in the specified election.

            Use this endpoint when the voter has lost their original receipt and needs
            to retrieve it again for verification purposes.

            Returns **404 Not Found** if the voter has not voted in the specified election.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Receipt retrieved successfully",
            content = @Content(schema = @Schema(implementation = VoteReceiptResponse.class))),
        @ApiResponse(responseCode = "401", description = "JWT token missing or invalid"),
        @ApiResponse(responseCode = "403", description = "Access denied — ELECTEUR role required"),
        @ApiResponse(responseCode = "404", description = "No vote found for this voter in the specified election")
    })
    public ResponseEntity<VoteReceiptResponse> getMyReceipt(
            @Parameter(
                description = "The ID of the election to retrieve the receipt for",
                required = true
            )
            @PathVariable Long electionId) {

        log.debug("GET /api/vote/my-receipt/{} — retrieving receipt for current voter", electionId);

        VoteReceiptResponse receipt = voteService.getMyReceipt(electionId);
        return ResponseEntity.ok(receipt);
    }

    // =========================================================
    // Integrity Check
    // =========================================================

    /**
     * Triggers a vote integrity verification for a specific election.
     * Only accessible to ELECTEUR role — provides a read-only integrity summary
     * without exposing internal vote data.
     *
     * For the full admin-level integrity report with tampered vote IDs,
     * use GET /api/admin/elections/{id}/integrity
     *
     * @param electionId the election to audit
     * @return 200 OK with a summary of the integrity verification result
     */
    @GetMapping("/integrity/{electionId}")
    @PreAuthorize("hasAnyRole('ELECTEUR', 'ADMIN', 'OBSERVATEUR')")
    @Operation(
        summary = "Verify vote integrity for an election",
        description = """
            Performs a lightweight integrity check on the vote records for the
            specified election and returns a summary result.

            Verifies that individual vote checksums match their expected values,
            detecting any post-registration tampering with ballot records.

            Returns whether the election's vote data is intact, without exposing
            individual vote content or voter identity.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Integrity check completed"),
        @ApiResponse(responseCode = "401", description = "JWT token missing or invalid"),
        @ApiResponse(responseCode = "404", description = "Election not found")
    })
    public ResponseEntity<VoteIntegrityResponse> checkVoteIntegrity(
            @Parameter(description = "The ID of the election to verify", required = true)
            @PathVariable Long electionId) {

        log.debug("GET /api/vote/integrity/{} — integrity check requested", electionId);

        VoteService.IntegrityReport report = voteService.verifyIntegrity(electionId);
        return ResponseEntity.ok(voteEndpointMapper.toVoteIntegrityResponse(report));
    }
}
