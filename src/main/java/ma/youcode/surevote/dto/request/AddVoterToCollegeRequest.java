package ma.youcode.surevote.dto.request;

import jakarta.validation.constraints.NotNull;

public record AddVoterToCollegeRequest(
    @NotNull(message = "L'identifiant de l'électeur est obligatoire")
    Long electeurId
) {}
