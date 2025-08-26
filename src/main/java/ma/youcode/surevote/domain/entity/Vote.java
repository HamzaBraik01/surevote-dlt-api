package ma.youcode.surevote.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents an anonymous ballot in the SUREVOTE platform.
 *
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  SECURITY DESIGN — CRITICAL: NO FOREIGN KEY TO ANY USER TABLE  ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * This entity deliberately contains NO reference (FK, field, or mapping)
 * to the Utilisateur, Electeur, or any identity-bearing table.
 *
 * This enforces the "double-barrier" anonymity pattern:
 *   - The VOTE table records WHAT was voted for (Election + Candidat).
 *   - The EMARGEMENT table records WHO voted (Electeur + Election).
 *   - No SQL JOIN between these two tables is architecturally possible.
 *
 * Even a privileged DBA with full database access cannot determine
 * which voter cast which ballot. This is a non-negotiable requirement
 * per the SUREVOTE security specification (NFR-01, FR-07).
 *
 * The horodatage (timestamp) is intentionally batch-processed or delayed
 * to prevent temporal correlation attacks linking Emargement to Vote records.
 */
@Entity
@Table(
    name = "votes",
    indexes = {
        @Index(name = "idx_vote_election", columnList = "election_id"),
        @Index(name = "idx_vote_candidat",  columnList = "candidat_id"),
        @Index(name = "idx_vote_horodatage", columnList = "horodatage")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The election this ballot belongs to.
     * This is the ONLY identity-adjacent link — it points to the election,
     * not to any voter.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "election_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_vote_election")
    )
    private Election election;

    /**
     * The candidate this ballot is cast for.
     * Links to Candidat only — never to Electeur.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "candidat_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_vote_candidat")
    )
    private Candidat candidat;

    /**
     * Timestamp of ballot insertion.
     *
     * SECURITY NOTE: This value is intentionally batch-processed or
     * slightly randomized before persistence to prevent temporal
     * correlation attacks (i.e., matching this timestamp against the
     * Emargement timestamp to infer voter identity).
     *
     * Do NOT expose this field via any API endpoint.
     */
    @Column(name = "horodatage", nullable = false)
    private LocalDateTime horodatage;

    /**
     * Checksum value for vote integrity verification.
     * Computed at insertion time and used to detect post-hoc tampering.
     * Part of the FR-12 Vote Integrity Checksum mechanism.
     */
    @Column(name = "checksum", length = 64)
    private String checksum;

    /**
     * Random salt used during checksum computation.
     * Stored alongside the vote to allow re-verification.
     * Makes each vote's checksum unique even for identical election+candidat pairs.
     */
    @Column(name = "checksum_salt", length = 36)
    private String checksumSalt;

    // -----------------------------------------------------------------------
    // Lifecycle callback — auto-set timestamp on creation
    // -----------------------------------------------------------------------

    /**
     * Automatically assigns the horodatage on first persistence.
     * A small random delay (0–30 seconds) is added to prevent
     * temporal correlation with the corresponding Emargement record.
     */
    @PrePersist
    protected void onPersist() {
        if (this.horodatage == null) {
            // Add a random offset between 0 and 30 seconds to obfuscate timing
            long randomOffsetSeconds = (long) (Math.random() * 30);
            this.horodatage = LocalDateTime.now().plusSeconds(randomOffsetSeconds);
        }
    }

    // -----------------------------------------------------------------------
    // equals / hashCode — id-based only (JPA best practice)
    // -----------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vote vote)) return false;
        return id != null && id.equals(vote.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        // SECURITY: Never expose candidat or election details in logs.
        // Only the internal ID is safe to log.
        return "Vote{id=" + id + ", horodatage=" + horodatage + "}";
    }
}
