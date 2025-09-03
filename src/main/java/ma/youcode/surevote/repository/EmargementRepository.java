package ma.youcode.surevote.repository;

import ma.youcode.surevote.domain.entity.Emargement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing Emargement (voter registry) records.
 *
 * The Emargement table is the first half of SUREVOTE's "double-barrier" architecture:
 *   - It records WHO voted in a given election (electeur_id + election_id).
 *   - It NEVER records WHAT was voted (no link to Vote or Candidat).
 *
 * The unique constraint on (electeur_id, election_id) enforces one-vote-per-voter
 * at the database level. This repository provides the service layer with the
 * queries needed to enforce this rule before the atomic transaction begins.
 */
@Repository
public interface EmargementRepository extends JpaRepository<Emargement, Long> {

    // =========================================================
    // Duplicate vote prevention — core security queries
    // =========================================================

    /**
     * Checks whether a given voter has already submitted a ballot in a given election.
     * This is the primary guard used BEFORE the @Transactional vote submission begins.
     *
     * @param electeurId  the ID of the voter
     * @param electionId  the ID of the election
     * @return true if an emargement record exists for this (voter, election) pair
     */
    boolean existsByElecteur_IdAndElection_Id(Long electeurId, Long electionId);

    /**
     * Retrieves the emargement record for a specific voter in a specific election.
     * Used to return the cryptographic receipt if the voter lost their original confirmation.
     *
     * @param electeurId  the ID of the voter
     * @param electionId  the ID of the election
     * @return an Optional containing the emargement, or empty if the voter has not yet voted
     */
    Optional<Emargement> findByElecteur_IdAndElection_Id(Long electeurId, Long electionId);

    // =========================================================
    // Receipt verification (public dashboard)
    // =========================================================

    /**
     * Finds an emargement by its cryptographic UUID receipt.
     * Used on the public verification dashboard where a voter enters their UUID
     * to confirm their participation was recorded — without revealing their identity
     * or their vote choice.
     *
     * @param recuCryptographique the UUID receipt string provided to the voter at vote time
     * @return an Optional containing the matching emargement, or empty if not found
     */
    Optional<Emargement> findByRecuCryptographique(String recuCryptographique);

    /**
     * Checks whether a given cryptographic receipt exists in the system.
     * Used as a lightweight verification check without loading the full entity.
     *
     * @param recuCryptographique the UUID receipt to verify
     * @return true if the receipt is present in the emargement table
     */
    boolean existsByRecuCryptographique(String recuCryptographique);

    // =========================================================
    // Election-level participation queries
    // =========================================================

    /**
     * Retrieves all emargements for a given election.
     * Used by the admin/observer layer to consult participation records.
     * IMPORTANT: Never expose the linked electeur's personal data via public APIs.
     *
     * @param electionId the ID of the election
     * @return list of all emargements for that election
     */
    List<Emargement> findByElection_Id(Long electionId);

    /**
     * Counts the total number of voters who participated in a given election.
     * Used for participation rate calculation and metrics dashboards.
     *
     * @param electionId the ID of the election
     * @return number of confirmed voters (emargement records) in the election
     */
    long countByElection_Id(Long electionId);

    /**
     * Retrieves all emargements for a specific voter across all elections.
     * Allows a voter to see their own participation history.
     *
     * @param electeurId the ID of the voter
     * @return list of all elections the voter has participated in
     */
    List<Emargement> findByElecteur_Id(Long electeurId);

    /**
     * Counts the total number of elections a voter has participated in.
     *
     * @param electeurId the ID of the voter
     * @return number of elections voted in
     */
    long countByElecteur_Id(Long electeurId);

    // =========================================================
    // Audit & metrics queries
    // =========================================================

    /**
     * Retrieves all emargements for a specific IP address.
     * Used for fraud detection — flags if multiple distinct voters
     * submitted ballots from the same IP address.
     *
     * @param adresseIp the IP address to investigate
     * @return list of emargements originating from that IP
     */
    List<Emargement> findByAdresseIp(String adresseIp);

    /**
     * Counts how many distinct voters participated from a given IP address
     * in a specific election. A value > 1 may indicate a shared network
     * or potential coordinated fraud attempt.
     *
     * @param adresseIp  the IP address
     * @param electionId the election to scope the check
     * @return count of distinct voter submissions from that IP in the election
     */
    @Query("""
        SELECT COUNT(e) FROM Emargement e
        WHERE e.adresseIp = :adresseIp
          AND e.election.id = :electionId
        """)
    long countByAdresseIpAndElection_Id(@Param("adresseIp") String adresseIp,
                                         @Param("electionId") Long electionId);

    /**
     * Computes the participation rate for a given election as a percentage.
     * The total eligible voter count is passed in as a parameter (from the
     * CollegeElectoral or global user count).
     *
     * @param electionId        the election to compute the rate for
     * @param totalEligibleVoters the total number of eligible voters
     * @return participation rate as a double (0.0 to 1.0), or 0.0 if no eligible voters
     */
    @Query("""
        SELECT CASE WHEN :totalEligibleVoters > 0
                    THEN (COUNT(e) * 1.0 / :totalEligibleVoters)
                    ELSE 0.0
               END
        FROM Emargement e
        WHERE e.election.id = :electionId
        """)
    Double computeParticipationRate(@Param("electionId") Long electionId,
                                     @Param("totalEligibleVoters") long totalEligibleVoters);

    /**
     * Returns participation counts grouped by election for all CLOTUREE/PUBLIEE elections.
     * Each row: [electionId, participantCount]
     * Used by MetricsService to compute average participation rate in a single query.
     */
    @Query("""
        SELECT e.election.id, COUNT(e)
        FROM Emargement e
        WHERE e.election.statut IN (
            ma.youcode.surevote.domain.enums.StatutElection.CLOTUREE,
            ma.youcode.surevote.domain.enums.StatutElection.PUBLIEE
        )
        GROUP BY e.election.id
        """)
    List<Object[]> countParticipantsGroupedByClosedElection();

    /**
     * Returns a list of election IDs in which a voter has already participated.
     */
    @Query("""
        SELECT e.election.id FROM Emargement e
        WHERE e.electeur.id = :electeurId
        """)
    List<Long> findVotedElectionIdsByElecteur(@Param("electeurId") Long electeurId);
}
