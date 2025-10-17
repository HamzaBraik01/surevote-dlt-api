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
import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import ma.youcode.surevote.dto.request.CreateUserRequest;
import ma.youcode.surevote.dto.request.UpdateRoleRequest;
import ma.youcode.surevote.dto.response.UserResponse;
import ma.youcode.surevote.service.UserService;
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
 * REST controller for Administrator-level user management operations.
 *
 * All endpoints in this controller are restricted to the ADMIN role.
 * Provides operations for:
 *  - Listing all registered users (with optional role filtering and search)
 *  - Retrieving individual user profiles
 *  - Updating user roles (dynamic RBAC assignment)
 *  - Activating and deactivating user accounts
 *  - Viewing platform-level user statistics
 *
 * Base path: /api/admin/users
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin — User Management", description = "CRUD operations for user management (ADMIN only)")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminUserController {

    private final UserService userService;

    // =========================================================
    // POST /api/admin/users — Create user (admin operation)
    // =========================================================

    /**
     * Creates a new user account with any role (ADMIN, ELECTEUR, OBSERVATEUR).
     * Only accessible by authenticated administrators.
     * A temporary password is generated and sent by email if not provided.
     *
     * POST /api/admin/users
     */
    @PostMapping
    @Operation(
        summary = "Create a new user (admin)",
        description = "Creates a new user account with the specified role. " +
                      "Unlike self-registration, this endpoint allows creating ADMIN and OBSERVATEUR accounts. " +
                      "A temporary password is generated and emailed if not provided."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User created successfully"),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "409", description = "Email or CIN already registered"),
        @ApiResponse(responseCode = "403", description = "Access denied — ADMIN role required")
    })
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("Admin creating user: email={}, role={}", request.getEmail(), request.getRole());
        UserResponse created = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // =========================================================
    // GET /api/admin/users — List all users
    // =========================================================

    /**
     * Returns a list of all registered users on the platform.
     * Supports optional filtering by role and keyword search.
     *
     * @param role    optional role filter (ADMIN, ELECTEUR, OBSERVATEUR)
     * @param keyword optional search keyword (matches nom, prenom, email)
     * @return list of UserResponse DTOs (no credential fields exposed)
     */
    @GetMapping
    @Operation(
        summary = "List all users",
        description = "Returns all registered users. Filter by role or search by keyword (nom, prenom, email)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Users retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied — ADMIN role required")
    })
    public ResponseEntity<List<UserResponse>> getAllUsers(
            @Parameter(description = "Filter by role: ADMIN, ELECTEUR, or OBSERVATEUR")
            @RequestParam(required = false) RoleUtilisateur role,

            @Parameter(description = "Search keyword — matches nom, prenom, or email (case-insensitive)")
            @RequestParam(required = false) String keyword) {

        log.debug("Admin requesting user list — role={}, keyword='{}'", role, keyword);

        List<UserResponse> users;

        if (keyword != null && !keyword.isBlank()) {
            users = userService.search(keyword.trim());
        } else if (role != null) {
            users = userService.findAllByRole(role);
        } else {
            users = userService.findAll();
        }

        return ResponseEntity.ok(users);
    }

    @GetMapping("/paged")
    @Operation(
        summary = "List users with pagination",
        description = "Returns paginated users for scalable admin listings."
    )
    @ApiResponse(responseCode = "200", description = "Paged users retrieved successfully")
    public ResponseEntity<Page<UserResponse>> getAllUsersPaged(
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field", example = "id")
            @RequestParam(defaultValue = "id") String sortBy,
            @Parameter(description = "Sort direction", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDir) {

        int clampedSize = Math.min(size, 100);
        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, clampedSize, sort);

        log.debug("Admin requesting paged users: page={}, size={}, sortBy={}, sortDir={}", page, clampedSize, sortBy, sortDir);
        return ResponseEntity.ok(userService.findAll(pageable));
    }

    // =========================================================
    // GET /api/admin/users/{id} — Get single user
    // =========================================================

    /**
     * Returns the profile of a specific user identified by their internal ID.
     *
     * @param id the user's primary key
     * @return the user's public profile DTO
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "Get user by ID",
        description = "Returns the public profile of a specific user by their internal database ID."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User found and returned"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "403", description = "Access denied — ADMIN role required")
    })
    public ResponseEntity<UserResponse> getUserById(
            @Parameter(description = "The internal database ID of the user", required = true)
            @PathVariable Long id) {

        log.debug("Admin fetching user id={}", id);
        return ResponseEntity.ok(userService.findById(id));
    }

    // =========================================================
    // PUT /api/admin/users/{id}/role — Update user role
    // =========================================================

    /**
     * Updates the role assigned to a specific user.
     *
     * This is an administrative RBAC operation. No privilege escalation
     * is possible via this endpoint — the security layer validates that
     * only an authenticated ADMIN can invoke it.
     *
     * @param id      the ID of the user whose role is being updated
     * @param request the new role to assign
     * @return the updated user profile DTO
     */
    @PutMapping("/{id}/role")
    @Operation(
        summary = "Update user role",
        description = "Assigns a new role to the specified user. " +
                      "Valid roles: ADMIN, ELECTEUR, OBSERVATEUR. ADMIN-only operation."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Role updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid role value"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "403", description = "Access denied — ADMIN role required")
    })
    public ResponseEntity<UserResponse> updateUserRole(
            @Parameter(description = "The internal database ID of the user", required = true)
            @PathVariable Long id,

            @Valid @RequestBody UpdateRoleRequest request) {

        log.info("Admin updating role for user id={} to {}", id, request.role());
        UserResponse updated = userService.updateRole(id, request.role());
        return ResponseEntity.ok(updated);
    }

    // =========================================================
    // DELETE /api/admin/users/{id} — Deactivate user
    // =========================================================

    /**
     * Deactivates a user account (soft delete).
     *
     * Hard deletion is not supported to preserve audit trail integrity.
     * A deactivated user cannot authenticate until reactivated by an admin.
     *
     * @param id the ID of the user to deactivate
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    @Operation(
        summary = "Deactivate user account",
        description = "Soft-deactivates a user account. The user will be unable to authenticate " +
                      "until reactivated. Hard deletion is not supported to preserve audit integrity."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "User deactivated successfully"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "403", description = "Access denied — ADMIN role required")
    })
    public ResponseEntity<Void> deactivateUser(
            @Parameter(description = "The internal database ID of the user to deactivate", required = true)
            @PathVariable Long id) {

        log.info("Admin deactivating user id={}", id);
        userService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================
    // PUT /api/admin/users/{id}/activate — Activate user
    // =========================================================

    /**
     * Reactivates a previously deactivated user account.
     *
     * @param id the ID of the user to reactivate
     * @return the updated user profile DTO
     */
    @PutMapping("/{id}/activate")
    @Operation(
        summary = "Activate user account",
        description = "Reactivates a previously deactivated user account, " +
                      "restoring their ability to authenticate."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User activated successfully"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "403", description = "Access denied — ADMIN role required")
    })
    public ResponseEntity<UserResponse> activateUser(
            @Parameter(description = "The internal database ID of the user to activate", required = true)
            @PathVariable Long id) {

        log.info("Admin activating user id={}", id);
        UserResponse updated = userService.setAccountEnabled(id, true);
        return ResponseEntity.ok(updated);
    }

    // =========================================================
    // PUT /api/admin/users/{id}/deactivate — Deactivate user (explicit)
    // =========================================================

    /**
     * Explicitly deactivates a user account (alternative to DELETE).
     * Useful when the client wants to distinguish deactivation from deletion semantically.
     *
     * @param id the ID of the user to deactivate
     * @return the updated user profile DTO
     */
    @PutMapping("/{id}/deactivate")
    @Operation(
        summary = "Deactivate user account (explicit PUT)",
        description = "Deactivates a user account via PUT, preserving the user record. " +
                      "Equivalent to DELETE /{id} but returns the updated user DTO."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User deactivated successfully"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "403", description = "Access denied — ADMIN role required")
    })
    public ResponseEntity<UserResponse> deactivateUserExplicit(
            @Parameter(description = "The internal database ID of the user to deactivate", required = true)
            @PathVariable Long id) {

        log.info("Admin deactivating (explicit PUT) user id={}", id);
        UserResponse updated = userService.setAccountEnabled(id, false);
        return ResponseEntity.ok(updated);
    }

    // =========================================================
    // GET /api/admin/users/stats — User statistics
    // =========================================================

    /**
     * Returns aggregated user statistics for the admin dashboard.
     *
     * @return a map of metric names to their values
     */
    @GetMapping("/stats")
    @Operation(
        summary = "Get user statistics",
        description = "Returns aggregated user counts by role and account status. " +
                      "Used for the admin dashboard overview panel."
    )
    @ApiResponse(responseCode = "200", description = "Statistics computed and returned")
    public ResponseEntity<Map<String, Long>> getUserStats() {
        log.debug("Admin requesting user statistics");

        Map<String, Long> stats = Map.of(
            "totalUsers",        userService.countAll(),
            "totalElecteurs",    userService.countByRole(RoleUtilisateur.ELECTEUR),
            "totalAdmins",       userService.countByRole(RoleUtilisateur.ADMIN),
            "totalObservateurs", userService.countByRole(RoleUtilisateur.OBSERVATEUR),
            "activeAccounts",    userService.countActive()
        );

        return ResponseEntity.ok(stats);
    }

    // =========================================================
    // GET /api/admin/users/role/{role} — List users by role
    // =========================================================

    /**
     * Returns all users with a specific role.
     * Convenience endpoint for role-filtered admin panels.
     *
     * @param role the role to filter by (ADMIN, ELECTEUR, or OBSERVATEUR)
     * @return list of users with the specified role
     */
    @GetMapping("/role/{role}")
    @Operation(
        summary = "List users by role",
        description = "Returns all users assigned to the specified role. " +
                      "Valid values: ADMIN, ELECTEUR, OBSERVATEUR."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Users retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid role value"),
        @ApiResponse(responseCode = "403", description = "Access denied — ADMIN role required")
    })
    public ResponseEntity<List<UserResponse>> getUsersByRole(
            @Parameter(description = "The role to filter by", required = true)
            @PathVariable RoleUtilisateur role) {

        log.debug("Admin fetching users with role: {}", role);
        return ResponseEntity.ok(userService.findAllByRole(role));
    }

    // =========================================================
    // GET /api/admin/users/me — Current admin profile
    // =========================================================

    /**
     * Returns the profile of the currently authenticated administrator.
     *
     * @return the authenticated admin's user profile
     */
    @GetMapping("/me")
    @Operation(
        summary = "Get current admin profile",
        description = "Returns the profile of the currently authenticated administrator."
    )
    @ApiResponse(responseCode = "200", description = "Profile returned successfully")
    public ResponseEntity<UserResponse> getCurrentAdminProfile() {
        log.debug("Admin requesting own profile");
        return ResponseEntity.ok(userService.getCurrentUserProfile());
    }
}
