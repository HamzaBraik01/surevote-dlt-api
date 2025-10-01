package ma.youcode.surevote.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating or updating a Candidat (candidate) in an election.
 * Used by the Administrator role via POST/PUT candidate management endpoints.
 *
 * All media URLs (photo, PDF program) are expected to be pre-uploaded paths
 * or external URLs — file upload is handled separately via a dedicated endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidatRequest {

    /**
     * Last name of the candidate.
     * Required, 1–100 characters.
     */
    @NotBlank(message = "Le nom du candidat est obligatoire")
    @Size(min = 1, max = 100, message = "Le nom doit contenir entre 1 et 100 caractères")
    private String nom;

    /**
     * First name of the candidate.
     * Required, 1–100 characters.
     */
    @NotBlank(message = "Le prénom du candidat est obligatoire")
    @Size(min = 1, max = 100, message = "Le prénom doit contenir entre 1 et 100 caractères")
    private String prenom;

    /**
     * Political party or organizational affiliation.
     * Optional — independent candidates may leave this blank.
     */
    @Size(max = 200, message = "L'affiliation ne peut pas dépasser 200 caractères")
    private String affiliationOuParti;

    /**
     * Full biographical text to display on the ballot page.
     * Optional but strongly recommended to inform voters.
     */
    @Size(max = 5000, message = "La biographie ne peut pas dépasser 5000 caractères")
    private String biographie;

    /**
     * URL or path to the candidate's profile photo.
     * Optional — defaults to a placeholder image if not provided.
     */
    @Size(max = 500, message = "L'URL de la photo ne peut pas dépasser 500 caractères")
    private String photoUrl;

    /**
     * URL or path to the candidate's electoral program PDF document.
     * Optional — allows voters to consult the full program before voting.
     */
    @Size(max = 500, message = "L'URL du programme PDF ne peut pas dépasser 500 caractères")
    private String programmePdfUrl;
}
