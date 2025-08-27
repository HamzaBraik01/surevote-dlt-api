package ma.youcode.surevote.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents the voter registry entry (émargement) for a given election.
 *
 * SECURITY DESIGN — "Double Barrier":
 * The Emargement records WHO voted (linked to an Electeur and an Election),
 * while the Vote table records WHAT was voted (linked only to Election + Candidat).
 * These two tables share NO common join key, making it cryptographically impossible
 * to link a voter's identity to their ballot choice.
 *
 * A unique constraint on (electeur_id, election_id) enforces the one-vote-per-voter rule
 * at the database level, providing a second barrier against duplicate votes.
 */
@Entity
@Table(
    name = "emargements",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_emargement_electeur_election",
            columnNames = {"electeur_id", "election_id"}
        ),
        @UniqueConstraint(
            name = "uq_emargement_recu",
            columnNames = {"recu_cryptographique"}
        )
    },
    indexes = {
        @Index(name = "idx_emargement_electeur",  columnList = "electeur_id"),
        @Index(name = "idx_emargement_election",  columnList = "election_id"),
        @Index(name = "idx_emargement_recu",      columnList = "recu_cryptographique"),
        @Index(name = "idx_emargement_electeur_election", columnList = "electeur_id, election_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Emargement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The voter who participated — establishes WHO voted.
     * This is the only table that references the voter's identity for a given election.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "electeur_id", nullable = false, updatable = false)
    private Electeur electeur;

    /**
     * The election in which the voter participated.
     * Together with electeur_id, forms the unique constraint preventing double voting.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false, updatable = false)
    private Election election;

    /**
     * Timestamp of the voter's participation.
     * Stored as-is; may be intentionally delayed or batch-processed
     * at the service layer to prevent temporal correlation with the Vote table.
     */
    @Column(name = "date_emargement", nullable = false, updatable = false)
    private LocalDateTime dateEmargement;

    /**
     * The cryptographic UUID receipt returned to the voter upon successful ballot submission.
     * The voter can use this UUID on the public verification dashboard to confirm
     * their participation was recorded — without revealing their vote choice.
     *
     * This value is unique, immutable after creation, and contains no information
     * about the candidate selected.
     */
    @Column(name = "recu_cryptographique", nullable = false, updatable = false, length = 36)
    private String recuCryptographique;

    /**
     * IP address from which the vote was submitted.
     * Stored for audit and fraud detection purposes.
     * Not used for any correlation with vote content.
     */
    @Column(name = "adresse_ip", length = 45)
    private String adresseIp;

    // -----------------------------------------------------------------------
    // equals / hashCode — based on id (JPA best practice)
    // -----------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Emargement that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Emargement{id=" + id
                + ", electionId=" + (election != null ? election.getId() : null)
                + ", dateEmargement=" + dateEmargement
                + ", recuCryptographique='" + recuCryptographique + "'}";
    }
}
