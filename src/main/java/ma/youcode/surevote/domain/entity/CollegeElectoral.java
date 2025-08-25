package ma.youcode.surevote.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents an Electoral College (Collège Électoral) — a named group of voters
 * that can be assigned to one or more elections to restrict ballot access.
 *
 * Design rationale:
 * - When an Election is linked to a CollegeElectoral, only voters belonging
 *   to that college are eligible to cast a ballot in that election.
 * - Voters not in the assigned college receive a 403 Forbidden when attempting
 *   to access the ballot, enforced at the service layer.
 *
 * Examples:
 * - "2nd Year Computer Science Students"
 * - "Full-Time Employees — Safi Campus"
 * - "Board Members — Fiscal Year 2025"
 */
@Entity
@Table(name = "colleges_electoraux")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollegeElectoral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The display name of this electoral college.
     * Must be unique and descriptive enough to identify the voter group.
     */
    @Column(nullable = false, length = 200)
    private String nom;

    /**
     * Optional detailed description of the college's membership criteria.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * The list of voters (Electeurs) that belong to this college.
     * Managed by the Administrator via the college management endpoints.
     */
    @OneToMany(mappedBy = "collegeElectoral", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Electeur> electeurs = new ArrayList<>();

    /**
     * The elections that are restricted to voters in this college.
     * An election can be open to all (no college) or restricted to one college.
     */
    @OneToMany(mappedBy = "collegeElectoral", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Election> elections = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Business methods
    // -----------------------------------------------------------------------

    /**
     * Returns the total number of registered voters in this college.
     *
     * @return voter count
     */
    public int getTailleCollege() {
        return electeurs != null ? electeurs.size() : 0;
    }

    /**
     * Checks whether a given voter is a member of this college.
     *
     * @param electeurId the ID of the voter to check
     * @return true if the voter belongs to this college
     */
    public boolean containsElecteur(Long electeurId) {
        if (electeurs == null) return false;
        return electeurs.stream()
                .anyMatch(e -> Objects.equals(e.getId(), electeurId));
    }

    // -----------------------------------------------------------------------
    // equals / hashCode — id-based (JPA best practice)
    // -----------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CollegeElectoral that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "CollegeElectoral{id=" + id + ", nom='" + nom + "'}";
    }
}
