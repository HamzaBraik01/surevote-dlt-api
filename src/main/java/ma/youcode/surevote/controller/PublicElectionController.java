package ma.youcode.surevote.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.dto.response.CandidatResponse;
import ma.youcode.surevote.dto.response.ElectionResponse;
import ma.youcode.surevote.dto.response.ResultatResponse;
import ma.youcode.surevote.dto.response.VoteReceiptResponse;
import ma.youcode.surevote.dto.response.endpoint.publicapi.ReceiptExistsResponse;
import ma.youcode.surevote.mapper.endpoint.PublicEndpointMapper;
import ma.youcode.surevote.service.CandidatService;
import ma.youcode.surevote.service.ElectionService;
import ma.youcode.surevote.service.ResultatService;
import ma.youcode.surevote.service.VoteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller exposing publicly accessible election data.
 *
 * These endpoints do NOT require authentication unless otherwise stated.
 * They expose only safe, non-sensitive information:
 *  - Elections in PLANIFIEE, OUVERTE, or PUBLIEE state
 *  - Candidate profiles (name, affiliation, biography, photo, programme)
 *  - Published results (PUBLIEE elections only)
 *  - Cryptographic receipt verification (public dashboard)
 *
 * BROUILLON (draft) elections are never exposed via these endpoints.
 *
 * Base paths:
 *   /api/elections         — public election listings
 *   /public/verify/{uuid}  — receipt verification dashboard
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "Public — Elections",
    description = "Publicly accessible election listings, candidate profiles, and published results. " +
                  "No authentication required (except receipt verification)."
)
public class PublicElectionController {

    private final ElectionService  electionService;
    private final CandidatService  candidatService;
    private final ResultatService  resultatService;
    private final VoteService voteService;
    private final PublicEndpointMapper publicEndpointMapper;

    // =========================================================
    // Election Listings
    // =========================================================

    /**
     * Returns all elections visible to the public:
     * PLANIFIEE, OUVERTE, and PUBLIEE elections.
     * BROUILLON drafts are always excluded.
     *
     * GET /api/elections
     *
     * @param keyword optional search keyword (title or description)
     * @return list of visible elections as response DTOs
     */
    @GetMapping("/api/elections")
    @Operation(
        summary = "List all publicly visible elections",
        description = """
            Returns all elections in PLANIFIEE, OUVERTE, or PUBLIEE state.
            BROUILLON (draft) elections are never included.

            Use the optional `keyword` parameter to search by title or description.
            """
    )
    @ApiResponse(responseCode = "200", description = "Elections retrieved successfully")
    public ResponseEntity<List<ElectionResponse>> getAllVisibleElections(
            @Parameter(description = "Optional search keyword — matches title or description")
            @RequestParam(required = false, defaultValue = "") String keyword) {

        log.debug("GET /api/elections — keyword='{}'", keyword);

        List<ElectionResponse> elections = keyword.isBlank()
                ? electionService.findAllVisible()
                : electionService.search(keyword.trim());

        return ResponseEntity.ok(elections);
    }

    /**
     * Returns all elections currently in OUVERTE state (active ballot period).
     * Used by the voter dashboard to display available elections to vote in.
     *
     * GET /api/elections/open
     *
     * @return list of currently open elections
     */
    @GetMapping("/api/elections/open")
    @Operation(
        summary = "List currently open elections",
        description = "Returns only elections in OUVERTE state — elections where voting is actively accepted. " +
                      "Used to populate the voter's available-elections list."
    )
    @ApiResponse(responseCode = "200", description = "Open elections retrieved successfully")
    public ResponseEntity<List<ElectionResponse>> getOpenElections() {
        log.debug("GET /api/elections/open");
        return ResponseEntity.ok(electionService.findAllOpen());
    }

    /**
     * Returns all elections with published results (PUBLIEE state).
     * Used by the public results dashboard.
     *
     * GET /api/elections/published
     *
     * @return list of published elections
     */
    @GetMapping("/api/elections/published")
    @Operation(
        summary = "List elections with published results",
        description = "Returns all elections in PUBLIEE state whose results are officially public."
    )
    @ApiResponse(responseCode = "200", description = "Published elections retrieved successfully")
    public ResponseEntity<List<ElectionResponse>> getPublishedElections() {
        log.debug("GET /api/elections/published");
        return ResponseEntity.ok(electionService.findAllPublished());
    }

    /**
     * Returns full details for a single election, including its candidate list.
     *
     * GET /api/elections/{id}
     *
     * @param id the election's primary key
     * @return the election's full response DTO including candidates
     */
    @GetMapping("/api/elections/{id}")
    @Operation(
        summary = "Get election details by ID",
        description = "Returns the full details of a specific election including its candidate list. " +
                      "Note: BROUILLON elections are accessible via this endpoint only to ADMIN users."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Election found and returned"),
        @ApiResponse(responseCode = "404", description = "Election not found")
    })
    public ResponseEntity<ElectionResponse> getElectionById(
            @Parameter(description = "The internal database ID of the election", required = true)
            @PathVariable Long id) {

        log.debug("GET /api/elections/{}", id);
        return ResponseEntity.ok(electionService.findById(id));
    }

    // =========================================================
    // Candidate Listings
    // =========================================================

    /**
     * Returns all candidates registered for a specific election.
     * Available once the election is in OUVERTE, CLOTUREE, or PUBLIEE state.
     *
     * GET /api/elections/{id}/candidates
     *
     * @param id the election's primary key
     * @return list of candidate response DTOs for that election
     */
    @GetMapping("/api/elections/{id}/candidates")
    @Operation(
        summary = "List candidates for an election",
        description = """
            Returns all candidates registered for the specified election,
            ordered alphabetically by last name.

            Note: Vote counts are NOT included in this response — only candidate profile data.
            Vote counts are only exposed via the results endpoint once the election is PUBLIEE.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Candidates retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Election not found")
    })
    public ResponseEntity<List<CandidatResponse>> getCandidatesByElection(
            @Parameter(description = "The internal database ID of the election", required = true)
            @PathVariable Long id) {

        log.debug("GET /api/elections/{}/candidates", id);
        return ResponseEntity.ok(candidatService.findAllByElection(id));
    }

    /**
     * Returns the profile of a single candidate by their ID.
     *
     * GET /api/elections/{electionId}/candidates/{candidatId}
     *
     * @param electionId  the election's primary key (for scoping)
     * @param candidatId  the candidate's primary key
     * @return the candidate's response DTO
     */
    @GetMapping("/api/elections/{electionId}/candidates/{candidatId}")
    @Operation(
        summary = "Get a specific candidate's profile",
        description = "Returns the public profile of a specific candidate within the given election."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Candidate found"),
        @ApiResponse(responseCode = "404", description = "Candidate or election not found")
    })
    public ResponseEntity<CandidatResponse> getCandidateById(
            @Parameter(description = "The election ID", required = true)
            @PathVariable Long electionId,

            @Parameter(description = "The candidate ID", required = true)
            @PathVariable Long candidatId) {

        log.debug("GET /api/elections/{}/candidates/{}", electionId, candidatId);
        return ResponseEntity.ok(candidatService.findById(candidatId));
    }

    // =========================================================
    // Published Results
    // =========================================================

    /**
     * Returns the official, publicly accessible results of a published election.
     * The election must be in PUBLIEE state for results to be accessible via this endpoint.
     *
     * GET /api/elections/{id}/results
     *
     * @param id the election's primary key
     * @return the full ResultatResponse with ranked candidates and participation stats
     */
    @GetMapping("/api/elections/{id}/results")
    @Operation(
        summary = "Get published election results",
        description = """
            Returns the official results of a published election.

            **Requirements:**
            - The election must be in `PUBLIEE` state.
            - Returns `422 Unprocessable Entity` for elections that are not yet published.

            **Response includes:**
            - Ranked list of candidates with vote counts and percentages
            - Participation rate and abstention statistics
            - Winner identification (and tie detection)
            - Vote table integrity checksum (FR-12)

            No voter identity or individual ballot information is ever exposed.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Results retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Election not found"),
        @ApiResponse(responseCode = "422", description = "Results not yet available — election not published")
    })
    public ResponseEntity<ResultatResponse> getPublicResults(
            @Parameter(description = "The internal database ID of the election", required = true)
            @PathVariable Long id) {

        log.debug("GET /api/elections/{}/results", id);
        return ResponseEntity.ok(resultatService.getPublicResults(id));
    }

    // =========================================================
    // Public Receipt Verification Dashboard (FR-08 / FR-14)
    // =========================================================

    /**
     * Verifies a cryptographic UUID receipt on the public verification dashboard.
     *
     * A voter can use their receipt UUID (received immediately after voting) to:
     *  1. Confirm that their participation was recorded in the system.
     *  2. Identify which election the receipt belongs to.
     *
     * This endpoint NEVER reveals:
     *  - The voter's identity
     *  - The candidate they voted for
     *  - Any link between the receipt and the actual Vote table record
     *
     * GET /public/verify/{recuCryptographique}
     *
     * @param recuCryptographique the UUID receipt provided to the voter at vote time
     * @return a VoteReceiptResponse confirming participation (no vote content exposed)
     */
    @GetMapping("/public/verify/{recuCryptographique}")
    @Operation(
        summary = "Verify a cryptographic vote receipt (public dashboard)",
        description = """
            Verifies that a voter's cryptographic receipt UUID is present in the system.

            **What this confirms:**
            - The receipt exists → the voter's participation was recorded.
            - The election the vote was cast in (by title only).
            - The timestamp of participation.

            **What this NEVER reveals:**
            - The voter's identity.
            - The candidate they voted for.
            - Any link to the anonymous Vote table record.

            This endpoint is fully public — no authentication required.
            The UUID is the only input needed for verification.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Receipt verified — participation confirmed"),
        @ApiResponse(responseCode = "404", description = "Receipt not found — UUID does not exist in the system")
    })
    public ResponseEntity<VoteReceiptResponse> verifyReceipt(
            @Parameter(
                description = "The UUID receipt provided to the voter after casting their ballot",
                required = true,
                example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable String recuCryptographique) {

        log.debug("GET /public/verify/{}", recuCryptographique);
        return ResponseEntity.ok(voteService.verifyReceipt(recuCryptographique));
    }

    /**
     * Checks whether a given UUID receipt exists in the system (lightweight existence check).
     * Returns a simple boolean result — useful for quick AJAX validation.
     *
     * GET /public/verify/{recuCryptographique}/exists
     *
     * @param recuCryptographique the UUID to check
     * @return 200 OK with { "exists": true/false, "uuid": "..." }
     */
    @GetMapping("/public/verify/{recuCryptographique}/exists")
    @Operation(
        summary = "Quick-check if a receipt UUID exists",
        description = "Returns a lightweight boolean check for whether a given UUID receipt " +
                      "exists in the participation registry. Does not load the full entity."
    )
    @ApiResponse(responseCode = "200", description = "Existence check completed")
    public ResponseEntity<ReceiptExistsResponse> receiptExists(
            @Parameter(description = "The UUID receipt to check", required = true)
            @PathVariable String recuCryptographique) {

        log.debug("GET /public/verify/{}/exists", recuCryptographique);
        boolean exists = resultatService.receiptExists(recuCryptographique);
        String message = exists
                ? "Ce reçu est valide — votre participation a été enregistrée."
                : "Ce reçu est introuvable — vérifiez que vous avez saisi le code correctement.";

        return ResponseEntity.ok(publicEndpointMapper.toReceiptExistsResponse(exists, recuCryptographique, message));
    }
}
