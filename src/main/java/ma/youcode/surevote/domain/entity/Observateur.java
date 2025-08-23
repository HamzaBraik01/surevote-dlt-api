package ma.youcode.surevote.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a read-only observer in the SUREVOTE platform.
 * Observers can consult aggregated election metrics and export audit journals
 * without accessing individual vote content or voter identity.
 */
@Entity
@DiscriminatorValue("OBSERVATEUR")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Observateur extends Utilisateur {

    /**
     * The organization or institution the observer represents.
     * Examples: "Electoral Commission", "Independent Audit Body", "University Council"
     */
    @Column(name = "organisme")
    private String organisme;
}
