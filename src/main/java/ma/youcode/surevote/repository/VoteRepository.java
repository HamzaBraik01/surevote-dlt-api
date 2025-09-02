package ma.youcode.surevote.repository;

import ma.youcode.surevote.domain.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for the {@link Vote} entity.
 *
 * SECURITY NOTE: The Vote table deliberately contains NO foreign key to any user table.
 * All queries in this repository operate exclusively on election_id and candidat_id —
 * never on any voter identity. This enforces the double-barrier anonymity architecture.
 *
 * Primary responsibilities:
 *  - Count votes per candidate for result aggregation
 *  - Compute total vote counts per election
 *  - Support checksum integrity verification (FR-12)
 */
@Repository
public interface VoteRepository extends JpaRepository<Vote, Long> {

    // =========================================================
    // Vote Counting — Result Aggregation
    // =========================================================

    /**
     * Counts the total number of ballots cast in a specific election.
     * Used to compute participation rates and verify result totals.
     *
     * @param electionId the ID of the election
     * @return total number of votes in that election
     */
    @Query("SELECT COUNT(v) FROM Vote v WHERE v.election.id = :electionId")
    long countByElectionId(@Param("electionId") Long electionId);

    /**
     * Counts the number of ballots cast for a specific candidate in a specific election.
     * This is the core aggregation used to compute final election results.
     *
     * @param electionId  the ID of the election
     * @param candidatId  the ID of the candidate
     * @return number of votes received by that candidate
     */
    @Query("SELECT COUNT(v) FROM Vote v WHERE v.election.id = :electionId AND v.candidat.id = :candidatId")
    long countByElectionIdAndCandidatId(@Param("electionId") Long electionId,
                                        @Param("candidatId") Long candidatId);

    /**
     * Returns a result set of [candidatId, voteCount] pairs for all candidates
     * in a given election, ordered by vote count descending.
     * Used to generate the full ranking for result publication.
     *
     * @param electionId the ID of the election
     * @return list of Object[] where [0]=candidatId (Long), [1]=voteCount (Long)
     */
    @Query("""
            SELECT v.candidat.id, COUNT(v)
            FROM Vote v
            WHERE v.election.id = :electionId
            GROUP BY v.candidat.id
            ORDER BY COUNT(v) DESC
            """)
    List<Object[]> countVotesGroupedByCandidatForElection(@Param("electionId") Long electionId);

    /**
     * Returns a rich result set of [candidatId, nom, prenom, affiliation, voteCount]
     * for all candidates in a given election, sorted by vote count descending.
     * Used directly by the result publication and report generation services.
     *
     * @param electionId the ID of the election
     * @return list of Object[] with candidat info and vote count
     */
    @Query("""
            SELECT c.id, c.nom, c.prenom, c.affiliationOuParti, COUNT(v)
            FROM Vote v
            JOIN v.candidat c
            WHERE v.election.id = :electionId
            GROUP BY c.id, c.nom, c.prenom, c.affiliationOuParti
            ORDER BY COUNT(v) DESC
            """)
    List<Object[]> findResultsForElection(@Param("electionId") Long electionId);

    // =========================================================
    // Integrity / Checksum Support (FR-12)
    // =========================================================

    /**
     * Retrieves all vote IDs and their stored checksums for a given election.
     * Used by the integrity monitoring service to detect any post-registration
     * tampering with vote records (FR-12).
     *
     * @param electionId the ID of the election to audit
     * @return list of Object[] where [0]=voteId (Long), [1]=checksum (String)
     */
    @Query("SELECT v.id, v.checksum FROM Vote v WHERE v.election.id = :electionId ORDER BY v.id ASC")
    List<Object[]> findVoteIdAndChecksumByElectionId(@Param("electionId") Long electionId);

    /**
     * Returns all votes for a given election, ordered by ID for deterministic checksum computation.
     * Used when recomputing the full table checksum during integrity audits.
     *
     * @param electionId the ID of the election
     * @return ordered list of votes (no voter identity fields — by design)
     */
    @Query("SELECT v FROM Vote v WHERE v.election.id = :electionId ORDER BY v.id ASC")
    List<Vote> findAllByElectionIdOrderById(@Param("electionId") Long electionId);

    // =========================================================
    // Existence Checks
    // =========================================================

    /**
     * Checks whether any votes have been cast in a given election.
     * Used to guard against premature result requests on empty elections.
     *
     * @param electionId the election to check
     * @return true if at least one vote exists for the election
     */
    @Query("SELECT COUNT(v) > 0 FROM Vote v WHERE v.election.id = :electionId")
    boolean existsByElectionId(@Param("electionId") Long electionId);

    /**
     * Checks whether any votes have been cast for a specific candidate.
     * Used before deleting a candidate to enforce referential integrity
     * at the business logic layer (candidates with votes cannot be removed).
     *
     * @param candidatId the candidate to check
     * @return true if at least one vote references this candidate
     */
    @Query("SELECT COUNT(v) > 0 FROM Vote v WHERE v.candidat.id = :candidatId")
    boolean existsByCandidatId(@Param("candidatId") Long candidatId);

    // =========================================================
    // Statistics
    // =========================================================

    /**
     * Returns the number of distinct elections that have received at least one vote.
     * Used for the observer metrics dashboard.
     *
     * @return count of elections with at least one vote
     */
    @Query("SELECT COUNT(DISTINCT v.election.id) FROM Vote v")
    long countDistinctElectionsWithVotes();

    /**
     * Returns the total number of votes cast across all elections.
     * Provides a global platform-level statistic.
     *
     * @return total vote count across the entire platform
     */
    @Query("SELECT COUNT(v) FROM Vote v")
    long countAllVotes();
}
