package ma.youcode.surevote.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a candidate running in a specific election.
 * A candidate is linked to exactly one election and can accumulate
 * anonymous votes — all without any reference to voter identities.
 *
 * Managed exclusively by Administrators (CRUD operations).
 */
@Entity
@Table(name = "candidats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Candidat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Last name of the candidate.
     */
    @Column(nullable = false, length = 100)
    private String nom;

    /**
     * First name of the candidate.
     */
    @Column(nullable = false, length = 100)
    private String prenom;

    /**
     * Political party or organizational affiliation.
     * Example: "Parti Progressiste", "Liste Indépendante"
     */
    @Column(name = "affiliation_ou_parti", length = 200)
    private String affiliationOuParti;

    /**
     * Full biographical text presented to voters on the ballot.
     * Stored as TEXT to support long descriptions.
     */
    @Column(name = "biographie", columnDefinition = "TEXT")
    private String biographie;

    /**
     * URL to the candidate's profile photo.
     * Can point to an uploaded file path or an external CDN URL.
     */
    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    /**
     * URL to the candidate's electoral program document (PDF).
     * Allows voters to review the candidate's platform before voting.
     */
    @Column(name = "programme_pdf_url", length = 500)
    private String programmePdfUrl;

    /**
     * The election this candidate is registered for.
     * A candidate belongs to exactly one election.
     *
     * IMPORTANT: This is the only foreign key relationship in the voting
     * subsystem that links to a non-user entity. The Vote table references
     * this Candidat to aggregate results — never through the voter.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_candidat_election"))
    private Election election;

    /**
     * Anonymous votes cast for this candidate.
     * This list contains NO voter identity — only election and timestamp data
     * per the double-barrier anonymity design.
     */
    @OneToMany(mappedBy = "candidat", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Vote> votes = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Business / convenience methods
    // -----------------------------------------------------------------------

    /**
     * Returns the candidate's full display name.
     *
     * @return "Prenom NOM" formatted string
     */
    public String getNomComplet() {
        return prenom + " " + nom.toUpperCase();
    }

    /**
     * Returns the total number of anonymous votes received by this candidate.
     * Only valid after the election has been closed (CLOTUREE / PUBLIEE status).
     *
     * @return count of votes
     */
    public long getTotalVotes() {
        return votes != null ? votes.size() : 0L;
    }

    // -----------------------------------------------------------------------
    // equals / hashCode — id-based (JPA best practice)
    // -----------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Candidat candidat)) return false;
        return Objects.equals(id, candidat.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Candidat{id=" + id
                + ", nom='" + nom + "'"
                + ", prenom='" + prenom + "'"
                + ", affiliation='" + affiliationOuParti + "'"
                + ", electionId=" + (election != null ? election.getId() : null)
                + "}";
    }
}
