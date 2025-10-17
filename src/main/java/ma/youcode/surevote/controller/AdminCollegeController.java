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
import ma.youcode.surevote.dto.request.AddVoterToCollegeRequest;
import ma.youcode.surevote.dto.request.CollegeRequest;
import ma.youcode.surevote.dto.response.endpoint.admin.CollegeMemberCountResponse;
import ma.youcode.surevote.dto.response.endpoint.admin.CollegeMembershipResponse;
import ma.youcode.surevote.dto.response.CollegeResponse;
import ma.youcode.surevote.dto.response.UserResponse;
import ma.youcode.surevote.mapper.endpoint.AdminEndpointMapper;
import ma.youcode.surevote.service.CollegeService;
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
 * REST controller for Administrator-only electoral college management.
 *
 * Base path: /api/admin/colleges
 * Required role: ADMIN
 *
 * An electoral college (CollegeElectoral) is a named group of voters
 * that can be assigned to one or more elections to restrict ballot access.
 *
 * Exposes endpoints for:
 *  - CRUD on CollegeElectoral entities
 *  - Adding and removing voters (Electeurs) from a college
 *  - Listing members of a college
 *  - Checking voter membership
 */
@RestController
@RequestMapping("/api/admin/colleges")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
@Tag(
    name = "Admin — Electoral Colleges",
    description = "Manage electoral colleges and voter membership assignments (ADMIN only)."
)
@SecurityRequirement(name = "Bearer Authentication")
public class AdminCollegeController {

    private final CollegeService collegeService;
    private final AdminEndpointMapper adminEndpointMapper;

    // =========================================================
    // CRUD — Create
    // =========================================================

    /**
     * Creates a new electoral college.
     *
     * POST /api/admin/colleges
     *
     * The college name must be unique across the platform.
     *
     * @param request validated college creation data (nom + optional description)
     * @return 201 Created with the new CollegeResponse DTO
     */
    @PostMapping
    @Operation(
        summary = "Create a new electoral college",
        description = """
            Creates a new named electoral college for grouping voters.

            The college name must be unique across the platform.
            Once created, voters can be assigned to this college via
            `POST /api/admin/colleges/{id}/voters`.

            An election can then be restricted to this college via the
            `collegeElectoralId` field in the election creation request.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "College created successfully"),
        @ApiResponse(responseCode = "400", description = "Validation failed — name is required"),
        @ApiResponse(responseCode = "409", description = "A college with that name already exists"),
        @ApiResponse(responseCode = "403", description = "Access denied — ADMIN role required")
    })
    public ResponseEntity<CollegeResponse> createCollege(
            @Valid @RequestBody CollegeRequest request) {

        log.info("POST /api/admin/colleges — nom='{}'", request.getNom());
        CollegeResponse created = collegeService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // =========================================================
    // CRUD — Read
    // =========================================================

    /**
     * Returns all electoral colleges on the platform.
     *
     * GET /api/admin/colleges
     *
     * Supports optional keyword search on the college name.
     *
     * @param keyword optional search keyword (case-insensitive match on college name)
     * @return 200 OK with list of CollegeResponse DTOs
     */
    @GetMapping
    @Operation(
        summary = "List all electoral colleges",
        description = "Returns all electoral colleges. Supports optional case-insensitive " +
                      "keyword search on the college name field."
    )
    @ApiResponse(responseCode = "200", description = "Colleges retrieved successfully")
    public ResponseEntity<List<CollegeResponse>> getAllColleges(
            @Parameter(description = "Optional keyword to filter colleges by name")
            @RequestParam(required = false) String keyword) {

        log.debug("GET /api/admin/colleges — keyword='{}'", keyword);

        List<CollegeResponse> colleges = (keyword != null && !keyword.isBlank())
                ? collegeService.search(keyword.trim())
                : collegeService.findAll();

        return ResponseEntity.ok(colleges);
    }

    @GetMapping("/paged")
    @Operation(
        summary = "List colleges with pagination",
        description = "Returns paginated electoral colleges for scalable admin listings."
    )
    @ApiResponse(responseCode = "200", description = "Paged colleges retrieved successfully")
    public ResponseEntity<Page<CollegeResponse>> getAllCollegesPaged(
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field", example = "nom")
            @RequestParam(defaultValue = "nom") String sortBy,
            @Parameter(description = "Sort direction", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDir) {

        int clampedSize = Math.min(size, 100);
        Sort sort = "desc".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, clampedSize, sort);

        return ResponseEntity.ok(collegeService.findAll(pageable));
    }

    /**
     * Returns a single electoral college by its internal ID.
     *
     * GET /api/admin/colleges/{id}
     *
     * @param id the college's primary key
     * @return 200 OK with the CollegeResponse DTO, or 404 if not found
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "Get college by ID",
        description = "Returns the details of a specific electoral college by its internal database ID."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "College found and returned"),
        @ApiResponse(responseCode = "404", description = "College not found"),
        @ApiResponse(responseCode = "403", description = "Access denied — ADMIN role required")
    })
    public ResponseEntity<CollegeResponse> getCollegeById(
            @Parameter(description = "Electoral college ID", required = true)
            @PathVariable Long id) {

        log.debug("GET /api/admin/colleges/{}", id);
        return ResponseEntity.ok(collegeService.findById(id));
    }

    // =========================================================
    // CRUD — Update
    // =========================================================

    /**
     * Updates the name and/or description of an existing electoral college.
     *
     * PUT /api/admin/colleges/{id}
     *
     * If the name is being changed, the new name must not conflict with another college.
     *
     * @param id      the ID of the college to update
     * @param request the updated college data
     * @return 200 OK with the updated CollegeResponse DTO
     */
    @PutMapping("/{id}")
    @Operation(
        summary = "Update an electoral college",
        description = "Updates the name and/or description of an existing electoral college. " +
                      "The new name must be unique if it differs from the current name."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "College updated successfully"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "404", description = "College not found"),
        @ApiResponse(responseCode = "409", description = "New name conflicts with existing college")
    })
    public ResponseEntity<CollegeResponse> updateCollege(
            @Parameter(description = "Electoral college ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody CollegeRequest request) {

        log.info("PUT /api/admin/colleges/{} — nom='{}'", id, request.getNom());
        return ResponseEntity.ok(collegeService.update(id, request));
    }

    // =========================================================
    // CRUD — Delete
    // =========================================================

    /**
     * Deletes an electoral college.
     *
     * DELETE /api/admin/colleges/{id}
     *
     * Before deletion:
     *  - All member voters are unlinked from this college.
     *  - All linked elections have their college reference cleared.
     *
     * @param id the ID of the college to delete
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete an electoral college",
        description = """
            Permanently deletes an electoral college.

            **Side effects on deletion:**
            - All voters assigned to this college are unlinked (their college is set to null).
            - All elections linked to this college have their `collegeElectoral` reference cleared,
              making them open to all voters.

            This operation is irreversible.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "College deleted successfully"),
        @ApiResponse(responseCode = "404", description = "College not found"),
        @ApiResponse(responseCode = "403", description = "Access denied — ADMIN role required")
    })
    public ResponseEntity<Void> deleteCollege(
            @Parameter(description = "Electoral college ID", required = true)
            @PathVariable Long id) {

        log.info("DELETE /api/admin/colleges/{}", id);
        collegeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================
    // Voter Membership — Read
    // =========================================================

    /**
     * Returns all voters (Electeurs) belonging to the specified college.
     *
     * GET /api/admin/colleges/{id}/voters
     *
     * @param id the college ID
     * @return 200 OK with list of UserResponse DTOs (no credential fields)
     */
    @GetMapping("/{id}/voters")
    @Operation(
        summary = "List members of a college",
        description = "Returns all voters currently assigned to the specified electoral college. " +
                      "Response contains safe UserResponse DTOs — no passwords or OTP fields."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Members retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "College not found"),
        @ApiResponse(responseCode = "403", description = "Access denied — ADMIN role required")
    })
    public ResponseEntity<List<UserResponse>> getCollegeMembers(
            @Parameter(description = "Electoral college ID", required = true)
            @PathVariable Long id) {

        log.debug("GET /api/admin/colleges/{}/voters", id);
        return ResponseEntity.ok(collegeService.findMembersByCollegeId(id));
    }

    /**
     * Returns the total member count for a specific college.
     *
     * GET /api/admin/colleges/{id}/voters/count
     *
     * @param id the college ID
     * @return 200 OK with a map containing the member count
     */
    @GetMapping("/{id}/voters/count")
    @Operation(
        summary = "Count members of a college",
        description = "Returns the total number of voters currently assigned to the specified college."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Count returned successfully"),
        @ApiResponse(responseCode = "404", description = "College not found")
    })
    public ResponseEntity<CollegeMemberCountResponse> countMembers(
            @Parameter(description = "Electoral college ID", required = true)
            @PathVariable Long id) {

        log.debug("GET /api/admin/colleges/{}/voters/count", id);
        long count = collegeService.countMembers(id);
        return ResponseEntity.ok(adminEndpointMapper.toCollegeMemberCountResponse(id, count));
    }

    // =========================================================
    // Voter Membership — Add
    // =========================================================

    /**
     * Assigns a voter (Electeur) to this electoral college.
     *
     * POST /api/admin/colleges/{id}/voters
     *
     * A voter can only belong to one college at a time.
     * If the voter is already in another college, they are moved to this one.
     * If the voter is already in this college, the operation is a no-op.
     *
     * @param id      the ID of the college to add the voter to
     * @param request the voter assignment request containing the electeurId
     * @return 200 OK with the updated CollegeResponse DTO
     */
    @PostMapping("/{id}/voters")
    @Operation(
        summary = "Add a voter to a college",
        description = """
            Assigns a registered voter (ELECTEUR) to this electoral college.

            **Behaviour:**
            - A voter can only belong to **one** college at a time.
            - If the voter is already in a different college, they are moved to this one.
            - If the voter is already in this college, the operation is idempotent (no change).
            - Only users with the `ELECTEUR` role can be assigned to a college.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Voter added to college successfully"),
        @ApiResponse(responseCode = "400", description = "Specified user is not an ELECTEUR"),
        @ApiResponse(responseCode = "404", description = "College or voter not found"),
        @ApiResponse(responseCode = "403", description = "Access denied — ADMIN role required")
    })
    public ResponseEntity<CollegeResponse> addVoterToCollege(
            @Parameter(description = "Electoral college ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody AddVoterToCollegeRequest request) {

        log.info("POST /api/admin/colleges/{}/voters — electeurId={}", id, request.electeurId());
        CollegeResponse updated = collegeService.addVoterToCollege(id, request.electeurId());
        return ResponseEntity.ok(updated);
    }

    // =========================================================
    // Voter Membership — Remove
    // =========================================================

    /**
     * Removes a voter (Electeur) from this electoral college.
     *
     * DELETE /api/admin/colleges/{id}/voters/{electeurId}
     *
     * If the voter is not in this college, the operation is a no-op.
     *
     * @param id         the ID of the college
     * @param electeurId the ID of the voter to remove
     * @return 200 OK with the updated CollegeResponse DTO
     */
    @DeleteMapping("/{id}/voters/{electeurId}")
    @Operation(
        summary = "Remove a voter from a college",
        description = """
            Removes a voter from the specified electoral college.

            **Behaviour:**
            - If the voter does not belong to this college, the operation is idempotent.
            - The voter's `collegeElectoral` reference is cleared (set to null).
            - The voter remains registered on the platform — only their college membership is removed.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Voter removed from college successfully"),
        @ApiResponse(responseCode = "404", description = "College or voter not found"),
        @ApiResponse(responseCode = "403", description = "Access denied — ADMIN role required")
    })
    public ResponseEntity<CollegeResponse> removeVoterFromCollege(
            @Parameter(description = "Electoral college ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Voter (Electeur) ID to remove", required = true)
            @PathVariable Long electeurId) {

        log.info("DELETE /api/admin/colleges/{}/voters/{}", id, electeurId);
        CollegeResponse updated = collegeService.removeVoterFromCollege(id, electeurId);
        return ResponseEntity.ok(updated);
    }

    // =========================================================
    // Membership Check
    // =========================================================

    /**
     * Checks whether a specific voter is a member of a specific college.
     *
     * GET /api/admin/colleges/{id}/voters/{electeurId}/membership
     *
     * @param id         the college ID
     * @param electeurId the voter ID to check
     * @return 200 OK with a map indicating membership status
     */
    @GetMapping("/{id}/voters/{electeurId}/membership")
    @Operation(
        summary = "Check voter membership in a college",
        description = "Returns whether a specific voter is currently assigned to the specified college."
    )
    @ApiResponse(responseCode = "200", description = "Membership status returned")
    public ResponseEntity<CollegeMembershipResponse> checkMembership(
            @Parameter(description = "Electoral college ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Voter (Electeur) ID to check", required = true)
            @PathVariable Long electeurId) {

        log.debug("GET /api/admin/colleges/{}/voters/{}/membership", id, electeurId);
        boolean isMember = collegeService.isVoterInCollege(id, electeurId);
        return ResponseEntity.ok(adminEndpointMapper.toCollegeMembershipResponse(id, electeurId, isMember));
    }
}
