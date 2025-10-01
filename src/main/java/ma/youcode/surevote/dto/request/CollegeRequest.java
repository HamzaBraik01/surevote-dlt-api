package ma.youcode.surevote.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating or updating a CollegeElectoral (electoral college).
 * Used by the Administrator role to group voters into restricted election pools.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollegeRequest {

    /**
     * Display name of the electoral college.
     * Must be unique and descriptive enough to identify the voter group.
     * Examples: "2nd Year Students", "Full-Time Employees Safi Campus"
     */
    @NotBlank(message = "Le nom du collège électoral est obligatoire")
    @Size(min = 3, max = 200, message = "Le nom doit contenir entre 3 et 200 caractères")
    private String nom;

    /**
     * Optional detailed description of the college's membership criteria.
     * Helps administrators understand the group's composition and purpose.
     */
    @Size(max = 2000, message = "La description ne peut pas dépasser 2000 caractères")
    private String description;
}
