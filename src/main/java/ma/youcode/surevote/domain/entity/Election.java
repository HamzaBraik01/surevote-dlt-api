package ma.youcode.surevote.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import ma.youcode.surevote.domain.enums.StatutElection;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a single electoral scrutin with its full lifecycle in SUREVOTE.
 *
 * State machine: BROUILLON → PLANIFIEE → OUVERTE → CLOTUREE → PUBLIEE
 * Transitions are automated via @Scheduled tasks and can be manually
 * overridden by administrators.
 *
 * Relations:
 *   - One Election → Many Candidats
 *   - One Election → Many Votes      (anonymous ballots)
 *   - One Election → Many Emargements (voter registry)
 *   - Many Elections → One CollegeElectoral (optional restriction)
 */
@Entity
@Table(
    name = "elections",
    indexes = {
        @Index(name = "idx_election_statut",     columnList = "statut"),
        @Index(name = "idx_election_date_debut", columnList = "date_debut"),
        @Index(name = "idx_election_date_fin",   columnList = "date_fin")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Election {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Short, descriptive title of the election.
     * Example: "Élection des représentants étudiants 2025"
     */
    @Column(nullable = false, length = 255)
    private String titre;

    /**
     * Detailed description of the election, its purpose, and rules.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Scheduled start date/time (UTC).
     * When this timestamp is reached, the election transitions to OUVERTE.
     */
    @Column(name = "date_debut", nullable = false)
    private LocalDateTime dateDebut;

    /**
     * Scheduled end date/time (UTC).
     * When this timestamp is reached, the election transitions to CLOTUREE.
     */
    @Column(name = "date_fin", nullable = false)
    private LocalDateTime dateFin;

    /**
     * Current lifecycle status of the election.
     * Defaults to BROUILLON (draft) on creation.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutElection statut = StatutElection.BROUILLON;

    /**
     * Timestamp when the election was officially created.
     */
    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    /**
     * Timestamp of the last update to this election record.
     */
    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    /**
     * Optional restriction to a specific electoral college.
     * If null, the election is open to all registered voters.
     * If set, only voters belonging to this college can access the ballot.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "college_electoral_id")
    private CollegeElectoral collegeElectoral;

    /**
     * List of candidates running in this election.
     * Managed by administrators (add/update/remove).
     */
    @OneToMany(mappedBy = "election", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Candidat> candidats = new ArrayList<>();

    /**
     * Anonymous ballot records — deliberately contain NO reference to any voter.
     * This is the core privacy-preserving mechanism of SUREVOTE.
     */
    @OneToMany(mappedBy = "election", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Vote> votes = new ArrayList<>();

    /**
     * Emargement records — tracks who participated WITHOUT revealing what they voted.
     * Forms the second half of the double-barrier separation architecture.
     */
    @OneToMany(mappedBy = "election", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Emargement> emargements = new ArrayList<>();

    // =========================================================
    // JPA Lifecycle Callbacks
    // =========================================================

    @PrePersist
    protected void onCreate() {
        this.dateCreation = LocalDateTime.now();
        this.dateModification = LocalDateTime.now();
        if (this.statut == null) {
            this.statut = StatutElection.BROUILLON;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.dateModification = LocalDateTime.now();
    }

    // =========================================================
    // State Machine — Transition Methods
    // =========================================================

    /**
     * Transitions the election from BROUILLON to PLANIFIEE.
     * Called when an admin finalises the election configuration.
     *
     * @throws IllegalStateException if the current state is not BROUILLON
     */
    public void planifier() {
        if (this.statut != StatutElection.BROUILLON) {
            throw new IllegalStateException(
                "Transition invalide : planifier() requiert l'état BROUILLON. État actuel : " + this.statut
            );
        }
        this.statut = StatutElection.PLANIFIEE;
    }

    /**
     * Transitions the election from PLANIFIEE to OUVERTE.
     * Called automatically by the scheduler when dateDebut is reached,
     * or manually by an administrator.
     *
     * @throws IllegalStateException if the current state is not PLANIFIEE
     */
    public void ouvrir() {
        if (this.statut != StatutElection.PLANIFIEE) {
            throw new IllegalStateException(
                "Transition invalide : ouvrir() requiert l'état PLANIFIEE. État actuel : " + this.statut
            );
        }
        this.statut = StatutElection.OUVERTE;
    }

    /**
     * Transitions the election from OUVERTE to CLOTUREE.
     * Called automatically by the scheduler when dateFin is reached,
     * or manually by an administrator.
     * Results are computed immediately after this transition.
     *
     * @throws IllegalStateException if the current state is not OUVERTE
     */
    public void cloturer() {
        if (this.statut != StatutElection.OUVERTE) {
            throw new IllegalStateException(
                "Transition invalide : cloturer() requiert l'état OUVERTE. État actuel : " + this.statut
            );
        }
        this.statut = StatutElection.CLOTUREE;
    }

    /**
     * Transitions the election from CLOTUREE to PUBLIEE.
     * Results become publicly accessible after this transition.
     *
     * @throws IllegalStateException if the current state is not CLOTUREE
     */
    public void publier() {
        if (this.statut != StatutElection.CLOTUREE) {
            throw new IllegalStateException(
                "Transition invalide : publier() requiert l'état CLOTUREE. État actuel : " + this.statut
            );
        }
        this.statut = StatutElection.PUBLIEE;
    }

    // =========================================================
    // Convenience Query Methods
    // =========================================================

    /**
     * @return true if voters can currently submit ballots
     */
    public boolean isOuverte() {
        return this.statut == StatutElection.OUVERTE;
    }

    /**
     * @return true if results are publicly available
     */
    public boolean isPubliee() {
        return this.statut == StatutElection.PUBLIEE;
    }

    /**
     * @return true if the election has been closed (results computed but not yet published)
     */
    public boolean isCloturee() {
        return this.statut == StatutElection.CLOTUREE;
    }

    /**
     * @return total number of ballots cast in this election
     */
    public int getTotalVotes() {
        return votes != null ? votes.size() : 0;
    }

    /**
     * @return total number of voters who participated (emargement count)
     */
    public int getTotalParticipants() {
        return emargements != null ? emargements.size() : 0;
    }

    // =========================================================
    // equals / hashCode
    // =========================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Election election)) return false;
        return Objects.equals(id, election.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Election{id=" + id + ", titre='" + titre + "', statut=" + statut + "}";
    }
}
