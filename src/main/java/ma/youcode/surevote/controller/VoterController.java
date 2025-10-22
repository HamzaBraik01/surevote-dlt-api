package ma.youcode.surevote.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.domain.entity.Utilisateur;
import ma.youcode.surevote.dto.response.ElectionResponse;
import ma.youcode.surevote.service.ElectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Voter-specific endpoints (ELECTEUR role).
 * Base path: /api/voter
 */
@RestController
@RequestMapping("/api/voter")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ELECTEUR')")
@Tag(name = "Voter", description = "Endpoints for registered voters (ELECTEUR role)")
@SecurityRequirement(name = "Bearer Authentication")
public class VoterController {

    private final ElectionService electionService;

    /**
     * Returns all open elections the authenticated voter is eligible to vote in,
     * considering their electoral college membership.
     *
     * GET /api/voter/elections/eligible
     */
    @GetMapping("/elections/eligible")
    @Operation(
        summary = "List elections the voter is eligible to vote in",
        description = """
            Returns all currently OUVERTE elections accessible to the authenticated voter.

            An election is included if:
            - It has no college restriction (open to all), OR
            - The voter belongs to the election's assigned electoral college.

            Elections the voter has already voted in are still included
            (use GET /api/vote/eligibility/{id} to check per-election status).
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Eligible elections returned"),
        @ApiResponse(responseCode = "401", description = "JWT token missing or invalid"),
        @ApiResponse(responseCode = "403", description = "ELECTEUR role required")
    })
    public ResponseEntity<List<ElectionResponse>> getEligibleElections(
            @AuthenticationPrincipal Utilisateur currentUser) {

        log.debug("GET /api/voter/elections/eligible — voter id={}", currentUser.getId());
        List<ElectionResponse> elections = electionService.findEligibleForVoter(currentUser.getId());
        return ResponseEntity.ok(elections);
    }
}
