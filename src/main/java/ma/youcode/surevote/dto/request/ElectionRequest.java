package ma.youcode.surevote.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request DTO for creating or updating an Election.
 * All fields are validated before reaching the service layer.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Payload de création/mise à jour d'une élection")
public class ElectionRequest {

    @Schema(description = "Titre de l'élection", example = "Élection des représentants 2026")
    @NotBlank(message = "Le titre de l'élection est obligatoire.")
    @Size(min = 5, max = 255, message = "Le titre doit contenir entre 5 et 255 caractères.")
    private String titre;

    @Schema(description = "Description de l'élection", example = "Scrutin annuel des représentants étudiants")
    @Size(max = 5000, message = "La description ne peut pas dépasser 5000 caractères.")
    private String description;

    @Schema(description = "Date/heure de début", example = "2026-06-01T09:00:00")
    @NotNull(message = "La date de début est obligatoire.")
    @Future(message = "La date de début doit être dans le futur.")
    private LocalDateTime dateDebut;

    @Schema(description = "Date/heure de fin", example = "2026-06-01T18:00:00")
    @NotNull(message = "La date de fin est obligatoire.")
    @Future(message = "La date de fin doit être dans le futur.")
    private LocalDateTime dateFin;

    /**
     * Optional ID of the CollegeElectoral to restrict this election to.
     * If null, the election is open to all registered voters.
     */
    @Schema(description = "ID du collège électoral (optionnel)", example = "2")
    private Long collegeElectoralId;
}
