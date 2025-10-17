package ma.youcode.surevote.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.dto.request.CandidatRequest;
import ma.youcode.surevote.dto.response.endpoint.admin.CandidateCountResponse;
import ma.youcode.surevote.dto.response.CandidatResponse;
import ma.youcode.surevote.mapper.endpoint.AdminEndpointMapper;
import ma.youcode.surevote.service.CandidatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for Administrator-only candidate management operations.
 *
 * Base path: /api/admin/elections/{electionId}/candidates
 * Required role: ADMIN
 *
 * Exposes CRUD endpoints for managing candidates registered in an election.
 *
 * Business rules enforced at the service layer:
 *  - Candidates can only be added/modified when the election is BROUILLON or PLANIFIEE.
 *  - Candidates cannot be removed once votes have been cast for them.
 *  - Duplicate candidates (same nom + prenom in the same election) are rejected.
 *  - At least 2 candidates are required before an election can transition to OUVERTE.
 *
 * All mutating operations are logged to the immutable audit trail via @Auditable AOP.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Candidates", description = "Candidate management for elections (ADMIN only)")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminCandidatController {

    private final CandidatService candidatService;
    private final AdminEndpointMapper adminEndpointMapper;

    // =========================================================
    // POST /api/admin/elections/{electionId}/candidates
    // =========================================================

    /**
     * Adds a new candidate to a specific election.
     *
     * The election must be in BROUILLON or PLANIFIEE state.
     * Duplicate candidates (same nom + prenom in same election) are rejected with 409.
     *
     * @param electionId the ID of the election to add the candidate to
     * @param request    the candidate profile data (validated)
     * @return 201 Created with the new CandidatResponse DTO
     */
    @PostMapping("/api/admin/elections/{electionId}/candidates")
    @Operation(
        summary = "Add a candidate to an election",
        description = """
            Registers a new candidate in the specified election.

            **Constraints:**
            - The election must be in `BROUILLON` or `PLANIFIEE` state.
            - Duplicate candidates (same nom + prenom) are rejected.
            - At least 2 candidates are required before the election can be opened.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Candidate added successfully"),
        @ApiResponse(responseCode = "400", description = "Validation error — missing or invalid fields"),
        @ApiResponse(responseCode = "404", description = "Election not found"),
        @ApiResponse(responseCode = "409", description = "Duplicate candidate (same name already registered)"),
        @ApiResponse(responseCode = "422", description = "Election is not in a modifiable state")
    })
    public ResponseEntity<CandidatResponse> addCandidat(
            @Parameter(description = "The ID of the election to add the candidate to", required = true)
            @PathVariable Long electionId,

            @Valid @RequestBody CandidatRequest request) {

        log.info("POST /api/admin/elections/{}/candidates — name='{} {}'",
                electionId, request.getPrenom(), request.getNom());
        CandidatResponse created = candidatService.addCandidat(electionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // =========================================================
    // GET /api/admin/elections/{electionId}/candidates
    // =========================================================

    /**
     * Returns all candidates registered for a specific election.
     *
     * @param electionId the election ID
     * @return 200 OK with list of CandidatResponse DTOs ordered by last name
     */
    @GetMapping("/api/admin/elections/{electionId}/candidates")
    @Operation(
        summary = "List all candidates for an election",
        description = "Returns all candidates registered in the specified election, ordered alphabetically by last name."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Candidates retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Election not found")
    })
    public ResponseEntity<List<CandidatResponse>> getCandidatsByElection(
            @Parameter(description = "The election ID", required = true)
            @PathVariable Long electionId) {

        log.debug("GET /api/admin/elections/{}/candidates", electionId);
        return ResponseEntity.ok(candidatService.findAllByElection(electionId));
    }

    // =========================================================
    // GET /api/admin/candidates/{id}
    // =========================================================

    /**
     * Returns a single candidate by their internal ID.
     *
     * @param id the candidate's primary key
     * @return 200 OK with the CandidatResponse, or 404 if not found
     */
    @GetMapping("/api/admin/candidates/{id}")
    @Operation(
        summary = "Get candidate by ID",
        description = "Returns the profile of a specific candidate identified by their internal ID."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Candidate found and returned"),
        @ApiResponse(responseCode = "404", description = "Candidate not found")
    })
    public ResponseEntity<CandidatResponse> getCandidatById(
            @Parameter(description = "The candidate's primary key", required = true)
            @PathVariable Long id) {

        log.debug("GET /api/admin/candidates/{}", id);
        return ResponseEntity.ok(candidatService.findById(id));
    }

    // =========================================================
    // PUT /api/admin/candidates/{id}
    // =========================================================

    /**
     * Updates an existing candidate's profile information.
     *
     * Only allowed when the linked election is in BROUILLON or PLANIFIEE state.
     *
     * @param id      the ID of the candidate to update
     * @param request the updated candidate data (validated)
     * @return 200 OK with the updated CandidatResponse
     */
    @PutMapping("/api/admin/candidates/{id}")
    @Operation(
        summary = "Update a candidate",
        description = """
            Updates the profile information of an existing candidate.

            **Constraints:**
            - The linked election must be in `BROUILLON` or `PLANIFIEE` state.
            - If the name changes, duplicate name validation is re-applied.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Candidate updated successfully"),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "404", description = "Candidate not found"),
        @ApiResponse(responseCode = "409", description = "Duplicate candidate name"),
        @ApiResponse(responseCode = "422", description = "Election is not in a modifiable state")
    })
    public ResponseEntity<CandidatResponse> updateCandidat(
            @Parameter(description = "The candidate's primary key", required = true)
            @PathVariable Long id,

            @Valid @RequestBody CandidatRequest request) {

        log.info("PUT /api/admin/candidates/{}", id);
        return ResponseEntity.ok(candidatService.updateCandidat(id, request));
    }

    // =========================================================
    // DELETE /api/admin/candidates/{id}
    // =========================================================

    /**
     * Removes a candidate from their election.
     *
     * Only allowed when:
     *  - The linked election is in BROUILLON or PLANIFIEE state.
     *  - No votes have been cast for this candidate.
     *
     * @param id the ID of the candidate to remove
     * @return 204 No Content on success
     */
    @DeleteMapping("/api/admin/candidates/{id}")
    @Operation(
        summary = "Remove a candidate from an election",
        description = """
            Permanently removes a candidate from their election.

            **Constraints:**
            - The linked election must be in `BROUILLON` or `PLANIFIEE` state.
            - The candidate cannot be removed if votes have already been cast for them.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Candidate removed successfully"),
        @ApiResponse(responseCode = "400", description = "Cannot delete — votes already cast for this candidate"),
        @ApiResponse(responseCode = "404", description = "Candidate not found"),
        @ApiResponse(responseCode = "422", description = "Election is not in a modifiable state")
    })
    public ResponseEntity<Void> deleteCandidat(
            @Parameter(description = "The candidate's primary key", required = true)
            @PathVariable Long id) {

        log.info("DELETE /api/admin/candidates/{}", id);
        candidatService.deleteCandidat(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================
    // PATCH /api/admin/candidates/{id}/photo
    // =========================================================

    /**
     * Updates the photo URL of a specific candidate.
     * Used after a dedicated file upload endpoint processes the image.
     *
     * @param id       the candidate ID
     * @param bodyMap  request body containing the "photoUrl" field
     * @return 200 OK with the updated CandidatResponse
     */
    @PatchMapping("/api/admin/candidates/{id}/photo")
    @Operation(
        summary = "Update candidate photo URL",
        description = "Updates the photo URL for a candidate after the image has been uploaded separately."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Photo URL updated"),
        @ApiResponse(responseCode = "400", description = "Missing or blank photoUrl field"),
        @ApiResponse(responseCode = "404", description = "Candidate not found")
    })
    public ResponseEntity<CandidatResponse> updatePhotoUrl(
            @Parameter(description = "The candidate's primary key", required = true)
            @PathVariable Long id,

            @RequestBody Map<String, String> bodyMap) {

        String photoUrl = bodyMap.get("photoUrl");
        if (photoUrl == null || photoUrl.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        log.info("PATCH /api/admin/candidates/{}/photo", id);
        return ResponseEntity.ok(candidatService.updatePhotoUrl(id, photoUrl.trim()));
    }

    // =========================================================
    // PATCH /api/admin/candidates/{id}/programme
    // =========================================================

    /**
     * Updates the electoral programme PDF URL for a specific candidate.
     * Used after a dedicated file upload endpoint processes the PDF document.
     *
     * @param id       the candidate ID
     * @param bodyMap  request body containing the "programmePdfUrl" field
     * @return 200 OK with the updated CandidatResponse
     */
    @PatchMapping("/api/admin/candidates/{id}/programme")
    @Operation(
        summary = "Update candidate programme PDF URL",
        description = "Updates the programme PDF URL for a candidate after the document has been uploaded separately."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Programme PDF URL updated"),
        @ApiResponse(responseCode = "400", description = "Missing or blank programmePdfUrl field"),
        @ApiResponse(responseCode = "404", description = "Candidate not found")
    })
    public ResponseEntity<CandidatResponse> updateProgrammePdfUrl(
            @Parameter(description = "The candidate's primary key", required = true)
            @PathVariable Long id,

            @RequestBody Map<String, String> bodyMap) {

        String programmePdfUrl = bodyMap.get("programmePdfUrl");
        if (programmePdfUrl == null || programmePdfUrl.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        log.info("PATCH /api/admin/candidates/{}/programme", id);
        return ResponseEntity.ok(candidatService.updateProgrammePdfUrl(id, programmePdfUrl.trim()));
    }

    // =========================================================
    // GET /api/admin/elections/{electionId}/candidates/count
    // =========================================================

    /**
     * Returns the total number of candidates registered for a given election.
     * Used for validation before transitioning the election to PLANIFIEE state.
     *
     * @param electionId the election ID
     * @return 200 OK with a map containing the candidate count
     */
    @GetMapping("/api/admin/elections/{electionId}/candidates/count")
    @Operation(
        summary = "Count candidates in an election",
        description = "Returns the number of candidates registered in the specified election. " +
                      "At least 2 candidates are required before the election can be opened."
    )
    @ApiResponse(responseCode = "200", description = "Count returned successfully")
    public ResponseEntity<CandidateCountResponse> countCandidats(
            @Parameter(description = "The election ID", required = true)
            @PathVariable Long electionId) {

        long count = candidatService.countByElection(electionId);
        boolean readyToOpen = count >= 2;
        String message = readyToOpen
                ? "L'élection dispose d'assez de candidats pour être ouverte."
                : "Au moins 2 candidats sont requis avant d'ouvrir l'élection. Actuellement: " + count;

        return ResponseEntity.ok(
                adminEndpointMapper.toCandidateCountResponse(electionId, count, readyToOpen, message)
        );
    }
}
