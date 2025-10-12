package ma.youcode.surevote.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for CollegeElectoral data exposed via the REST API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollegeResponse {

    private Long id;

    private String nom;

    private String description;

    /** Number of voters currently assigned to this college. */
    private Integer nombreElecteurs;

    /** Number of elections currently restricted to this college. */
    private Integer nombreElections;
}
