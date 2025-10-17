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
import ma.youcode.surevote.dto.request.ElectionRequest;
import ma.youcode.surevote.dto.response.endpoint.admin.ElectionIntegrityReportResponse;
import ma.youcode.surevote.dto.response.endpoint.admin.ElectionStatsResponse;
import ma.youcode.surevote.dto.response.ElectionResponse;
import ma.youcode.surevote.dto.response.ResultatResponse;
import ma.youcode.surevote.mapper.endpoint.AdminEndpointMapper;
import ma.youcode.surevote.service.ElectionService;
import ma.youcode.surevote.service.ResultatService;
import ma.youcode.surevote.service.VoteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for Administrator-only election management operations.
 *
 * Base path: /api/admin/elections
 * Required role: ADMIN
 *
 * Exposes endpoints for:
 *  - Full CRUD on elections
 *  - State machine transitions (plan, open, close, publish)
 *  - Pre-publication result viewing (CLOTUREE state)
 *  - Vote integrity verification (FR-12)
 *
 * All endpoints are protected by Spring Security RBAC (hasRole("ADMIN")).
 * All mutating operations are logged to the immutable audit trail via @Auditable AOP.
 */
@RestController
@RequestMapping("/api/admin/elections")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Elections", description = "Election management endpoints (ADMIN only)")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminElectionController {

    private final ElectionService electionService;
    private final ResultatService resultatService;
    private final VoteService     voteService;
    private final AdminEndpointMapper adminEndpointMapper;

    // =========================================================
    // CRUD — Create
    // =========================================================

    /**
     * Creates a new election in BROUILLON (draft) state.
     *
     * POST /api/admin/elections
     *
     * @param request validated election configuration data
     * @return 201 Created with the new election DTO
     */
    @PostMapping
    @Operation(
        summary = "Create a new election",
        description = "Creates a new election in BROUILLON (draft) state. " +
                      "The election must be configured and have at least 2 candidates " +
                      "before it can be transitioned to PLANIFIEE."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Election created successfully",
            content = @Content(schema = @Schema(implementation = ElectionResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation error — invalid dates or missing fields"),
        @ApiResponse(responseCode = "403", description = "Access denied — ADMIN role required"),
        @ApiResponse(responseCode = "404", description = "CollegeElectoral not found (if collegeElectoralId provided)")
    })
    public ResponseEntity<ElectionResponse> createElection(
            @Valid @RequestBody ElectionRequest request) {
        log.debug("POST /api/admin/elections — titre='{}'", request.getTitre());
        ElectionResponse created = electionService.createElection(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // =========================================================
    // CRUD — Read
    // =========================================================

    /**
     * Returns all elections (all statuses — admin view).
     *
     * GET /api/admin/elections
     *
     * @return 200 OK with list of all elections
     */
    @GetMapping
    @Operation(
        summary = "List all elections (admin view)",
        description = "Returns all elections regardless of status, including BROUILLON drafts hidden from voters."
    )
    @ApiResponse(responseCode = "200", description = "Elections retrieved successfully")
    public ResponseEntity<List<ElectionResponse>> getAllElections() {
        log.debug("GET /api/admin/elections");
        return ResponseEntity.ok(electionService.findAll());
    }

    @GetMapping("/paged")
    @Operation(
        summary = "List elections with pagination",
        description = "Returns paginated elections for scalable admin dashboards."
    )
    @ApiResponse(responseCode = "200", description = "Paged elections retrieved successfully")
    public ResponseEntity<Page<ElectionResponse>> getAllElectionsPaged(
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field", example = "dateDebut")
            @RequestParam(defaultValue = "dateDebut") String sortBy,
            @Parameter(description = "Sort direction", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDir) {

        int clampedSize = Math.min(size, 100);
        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, clampedSize, sort);

        return ResponseEntity.ok(electionService.findAll(pageable));
    }

    /**
     * Returns a single election by ID, including its candidate list.
     *
     * GET /api/admin/elections/{id}
     *
     * @param id the election primary key
     * @return 200 OK with full election details, or 404 if not found
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "Get election details by ID",
        description = "Returns full election details including the candidate list."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Election found"),
        @ApiResponse(responseCode = "404", description = "Election not found")
    })
    public ResponseEntity<ElectionResponse> getElectionById(
            @Parameter(description = "Election ID", required = true)
            @PathVariable Long id) {
        log.debug("GET /api/admin/elections/{}", id);
        return ResponseEntity.ok(electionService.findById(id));
    }

    /**
     * Returns elections filtered by a specific status.
     *
     * GET /api/admin/elections/by-status?statut=OUVERTE
     *
     * @param statut the StatutElection enum value to filter by
     * @return 200 OK with list of matching elections
     */
    @GetMapping("/by-status")
    @Operation(
        summary = "Filter elections by status",
        description = "Returns elections filtered by lifecycle status: " +
                      "BROUILLON, PLANIFIEE, OUVERTE, CLOTUREE, or PUBLIEE."
    )
    @ApiResponse(responseCode = "200", description = "Elections retrieved")
    public ResponseEntity<List<ElectionResponse>> getElectionsByStatus(
            @Parameter(description = "Election status filter", required = true,
                       example = "OUVERTE")
            @RequestParam ma.youcode.surevote.domain.enums.StatutElection statut) {
        log.debug("GET /api/admin/elections/by-status?statut={}", statut);
        return ResponseEntity.ok(electionService.findByStatut(statut));
    }

    /**
     * Full-text search across election titles and descriptions.
     *
     * GET /api/admin/elections/search?keyword=représentants
     *
     * @param keyword the search term
     * @return 200 OK with list of matching elections
     */
    @GetMapping("/search")
    @Operation(
        summary = "Search elections by keyword",
        description = "Case-insensitive full-text search across election title and description."
    )
    @ApiResponse(responseCode = "200", description = "Search results returned")
    public ResponseEntity<List<ElectionResponse>> searchElections(
            @Parameter(description = "Search keyword", example = "représentants")
            @RequestParam(defaultValue = "") String keyword) {
        log.debug("GET /api/admin/elections/search?keyword='{}'", keyword);
        return ResponseEntity.ok(electionService.search(keyword));
    }

    // =========================================================
    // CRUD — Update
    // =========================================================

    /**
     * Updates the configuration of an existing election.
     * Only allowed when the election is in BROUILLON or PLANIFIEE state.
     *
     * PUT /api/admin/elections/{id}
     *
     * @param id      the election to update
     * @param request the new configuration data
     * @return 200 OK with updated election, or 400 if in wrong state
     */
    @PutMapping("/{id}")
    @Operation(
        summary = "Update an election",
        description = "Updates election configuration. Only allowed when election is in " +
                      "BROUILLON or PLANIFIEE state. Active or closed elections cannot be modified."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Election updated successfully"),
        @ApiResponse(responseCode = "400", description = "Election is in a non-modifiable state (OUVERTE/CLOTUREE/PUBLIEE)"),
        @ApiResponse(responseCode = "404", description = "Election not found")
    })
    public ResponseEntity<ElectionResponse> updateElection(
            @Parameter(description = "Election ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody ElectionRequest request) {
        log.debug("PUT /api/admin/elections/{}", id);
        return ResponseEntity.ok(electionService.updateElection(id, request));
    }

    // =========================================================
    // CRUD — Delete
    // =========================================================

    /**
     * Deletes a draft election permanently.
     * Only elections in BROUILLON state can be deleted.
     *
     * DELETE /api/admin/elections/{id}
     *
     * @param id the election to delete
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete a draft election",
        description = "Permanently deletes an election. Only BROUILLON (draft) elections " +
                      "without any votes can be deleted."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Election deleted successfully"),
        @ApiResponse(responseCode = "400", description = "Election cannot be deleted (not in BROUILLON state or has votes)"),
        @ApiResponse(responseCode = "404", description = "Election not found")
    })
    public ResponseEntity<Void> deleteElection(
            @Parameter(description = "Election ID", required = true)
            @PathVariable Long id) {
        log.debug("DELETE /api/admin/elections/{}", id);
        electionService.deleteElection(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================
    // State Machine — Transitions
    // =========================================================

    /**
     * Transitions an election from BROUILLON → PLANIFIEE.
     * Requires at least 2 candidates to be registered.
     *
     * POST /api/admin/elections/{id}/plan
     *
     * @param id the election to plan
     * @return 200 OK with updated election in PLANIFIEE state
     */
    @PostMapping("/{id}/plan")
    @Operation(
        summary = "Plan election (BROUILLON → PLANIFIEE)",
        description = "Transitions a draft election to PLANIFIEE state, marking it as ready " +
                      "for automated opening at dateDebut. Requires at least 2 candidates."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Election is now PLANIFIEE"),
        @ApiResponse(responseCode = "400", description = "Invalid transition or insufficient candidates"),
        @ApiResponse(responseCode = "404", description = "Election not found")
    })
    public ResponseEntity<ElectionResponse> planElection(
            @Parameter(description = "Election ID", required = true)
            @PathVariable Long id) {
        log.info("POST /api/admin/elections/{}/plan", id);
        return ResponseEntity.ok(electionService.planifierElection(id));
    }

    /**
     * Manually opens an election: PLANIFIEE → OUVERTE.
     * Allows early opening before the scheduled dateDebut.
     *
     * POST /api/admin/elections/{id}/open
     *
     * @param id the election to open
     * @return 200 OK with updated election in OUVERTE state
     */
    @PostMapping("/{id}/open")
    @Operation(
        summary = "Manually open election (PLANIFIEE → OUVERTE)",
        description = "Manually opens a planned election before its scheduled start date. " +
                      "Voters can immediately submit ballots once the election is OUVERTE."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Election is now OUVERTE — voting is active"),
        @ApiResponse(responseCode = "400", description = "Election is not in PLANIFIEE state"),
        @ApiResponse(responseCode = "404", description = "Election not found")
    })
    public ResponseEntity<ElectionResponse> openElection(
            @Parameter(description = "Election ID", required = true)
            @PathVariable Long id) {
        log.info("POST /api/admin/elections/{}/open", id);
        return ResponseEntity.ok(electionService.ouvrirScrutin(id));
    }

    /**
     * Manually closes an election: OUVERTE → CLOTUREE.
     * Voting stops immediately. Results are computed server-side upon closure.
     *
     * POST /api/admin/elections/{id}/close
     *
     * @param id the election to close
     * @return 200 OK with updated election in CLOTUREE state
     */
    @PostMapping("/{id}/close")
    @Operation(
        summary = "Manually close election (OUVERTE → CLOTUREE)",
        description = "Manually closes an active election before its scheduled end date. " +
                      "All ballot submissions are immediately rejected after this point. " +
                      "Results are computed automatically upon closure."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Election is now CLOTUREE — voting has ended"),
        @ApiResponse(responseCode = "400", description = "Election is not in OUVERTE state"),
        @ApiResponse(responseCode = "404", description = "Election not found")
    })
    public ResponseEntity<ElectionResponse> closeElection(
            @Parameter(description = "Election ID", required = true)
            @PathVariable Long id) {
        log.info("POST /api/admin/elections/{}/close", id);
        return ResponseEntity.ok(electionService.cloturerScrutin(id));
    }

    /**
     * Publishes election results: CLOTUREE → PUBLIEE.
     * Results become publicly accessible after this transition.
     *
     * POST /api/admin/elections/{id}/publish
     *
     * @param id the election to publish
     * @return 200 OK with updated election in PUBLIEE state
     */
    @PostMapping("/{id}/publish")
    @Operation(
        summary = "Publish results (CLOTUREE → PUBLIEE)",
        description = "Officially publishes the election results, making them accessible " +
                      "to all users on the public results dashboard."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Election is now PUBLIEE — results are public"),
        @ApiResponse(responseCode = "400", description = "Election is not in CLOTUREE state"),
        @ApiResponse(responseCode = "404", description = "Election not found")
    })
    public ResponseEntity<ElectionResponse> publishResults(
            @Parameter(description = "Election ID", required = true)
            @PathVariable Long id) {
        log.info("POST /api/admin/elections/{}/publish", id);
        return ResponseEntity.ok(electionService.publierResultats(id));
    }

    // =========================================================
    // Results (pre-publication admin view)
    // =========================================================

    /**
     * Returns election results accessible to the administrator from CLOTUREE state.
     * Allows admin to review before officially publishing.
     *
     * GET /api/admin/elections/{id}/results
     *
     * @param id the election ID
     * @return 200 OK with full ResultatResponse
     */
    @GetMapping("/{id}/results")
    @Operation(
        summary = "View election results (admin — pre-publication)",
        description = "Returns computed election results accessible from CLOTUREE state. " +
                      "Admin can review results before publishing them to the public."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Results computed and returned"),
        @ApiResponse(responseCode = "422", description = "Results not yet available — election not closed"),
        @ApiResponse(responseCode = "404", description = "Election not found")
    })
    public ResponseEntity<ResultatResponse> getElectionResults(
            @Parameter(description = "Election ID", required = true)
            @PathVariable Long id) {
        log.debug("GET /api/admin/elections/{}/results", id);
        return ResponseEntity.ok(resultatService.getResultsForAdmin(id));
    }

    // =========================================================
    // Vote Statistics
    // =========================================================

    /**
     * Returns the current vote count and participation statistics for an election.
     *
     * GET /api/admin/elections/{id}/stats
     *
     * @param id the election ID
     * @return 200 OK with participation statistics map
     */
    @GetMapping("/{id}/stats")
    @Operation(
        summary = "Get real-time election participation statistics",
        description = "Returns the current vote and participation counts for an election. " +
                      "Available for all election statuses for monitoring purposes."
    )
    @ApiResponse(responseCode = "200", description = "Statistics returned")
    public ResponseEntity<ElectionStatsResponse> getElectionStats(
            @Parameter(description = "Election ID", required = true)
            @PathVariable Long id) {
        log.debug("GET /api/admin/elections/{}/stats", id);

        long totalVotes        = voteService.countVotesByElection(id);
        long totalParticipants = voteService.countParticipantsByElection(id);
        return ResponseEntity.ok(
                adminEndpointMapper.toElectionStatsResponse(
                        id, totalVotes, totalParticipants, totalVotes != totalParticipants
                )
        );
    }

    // =========================================================
    // Vote Integrity (FR-12)
    // =========================================================

    /**
     * Computes and returns the SHA-256 checksum of the Vote table for a given election.
     * Used to detect any post-registration tampering with ballot records.
     *
     * GET /api/admin/elections/{id}/integrity
     *
     * @param id the election ID
     * @return 200 OK with the checksum string and verification details
     */
    @GetMapping("/{id}/integrity")
    @Operation(
        summary = "Compute vote table integrity checksum (FR-12)",
        description = "Computes a SHA-256 checksum of all Vote records for the specified election. " +
                      "Any post-registration alteration to vote records will produce a different checksum, " +
                      "immediately signaling a data integrity violation."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Checksum computed successfully"),
        @ApiResponse(responseCode = "404", description = "Election not found")
    })
    public ResponseEntity<ElectionIntegrityReportResponse> verifyIntegrity(
            @Parameter(description = "Election ID", required = true)
            @PathVariable Long id) {
        log.info("GET /api/admin/elections/{}/integrity — integrity check requested", id);

        String checksum            = resultatService.computeVoteTableChecksum(id);
        long   totalVotes          = voteService.countVotesByElection(id);
        long   totalParticipants   = voteService.countParticipantsByElection(id);
        List<Long> tampered        = resultatService.detectTamperedVotes(id);
        String verifiedAt = java.time.LocalDateTime.now().toString();

        if (!tampered.isEmpty()) {
            log.error("INTEGRITY VIOLATION for election id={}: tampered votes={}", id, tampered);
        }

        return ResponseEntity.ok(
                adminEndpointMapper.toElectionIntegrityReportResponse(
                        id,
                        checksum,
                        totalVotes,
                        totalParticipants,
                        totalVotes != totalParticipants,
                        tampered,
                        !tampered.isEmpty(),
                        verifiedAt
                )
        );
    }
}
