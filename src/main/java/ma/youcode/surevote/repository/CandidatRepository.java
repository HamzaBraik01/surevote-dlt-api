package ma.youcode.surevote.repository;

import ma.youcode.surevote.domain.entity.Candidat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Candidat} entities.
 *
 * Provides data access operations for candidate management,
 * including election-scoped queries used during ballot display
 * and result aggregation.
 */
@Repository
public interface CandidatRepository extends JpaRepository<Candidat, Long> {

    /**
     * Retrieves all candidates registered for a specific election.
     * Used to populate the ballot page for eligible voters.
     *
     * @param electionId the ID of the election
     * @return list of candidates for that election, ordered by last name
     */
    @Query("SELECT c FROM Candidat c WHERE c.election.id = :electionId ORDER BY c.nom ASC, c.prenom ASC")
    List<Candidat> findByElectionId(@Param("electionId") Long electionId);

    /**
     * Counts the total number of candidates in a given election.
     * Useful for validation (e.g., ensuring at least 2 candidates before opening).
     *
     * @param electionId the ID of the election
     * @return count of candidates
     */
    long countByElectionId(Long electionId);

    /**
     * Finds a specific candidate belonging to a specific election.
     * Used to validate that a submitted vote references a valid candidate
     * for the given election (prevents cross-election vote injection).
     *
     * @param candidatId the ID of the candidate
     * @param electionId the ID of the election
     * @return Optional containing the candidate if found and linked to that election
     */
    @Query("SELECT c FROM Candidat c WHERE c.id = :candidatId AND c.election.id = :electionId")
    Optional<Candidat> findByIdAndElectionId(
            @Param("candidatId") Long candidatId,
            @Param("electionId") Long electionId
    );

    /**
     * Retrieves all candidates for an election with their vote counts.
     * Used for result aggregation after election closure.
     * Returns a projection of [Candidat, voteCount] pairs.
     *
     * @param electionId the ID of the closed/published election
     * @return list of object arrays: [Candidat candidat, Long voteCount]
     */
    @Query("""
            SELECT c, COUNT(v.id) AS voteCount
            FROM Candidat c
            LEFT JOIN Vote v ON v.candidat.id = c.id AND v.election.id = :electionId
            WHERE c.election.id = :electionId
            GROUP BY c.id
            ORDER BY voteCount DESC, c.nom ASC
            """)
    List<Object[]> findCandidatsWithVoteCount(@Param("electionId") Long electionId);

    /**
     * Checks whether a candidate with the given full name already exists
     * in a specific election. Used to prevent duplicate candidate registrations.
     *
     * @param nom        last name
     * @param prenom     first name
     * @param electionId the election to check in
     * @return true if a candidate with that name already exists in the election
     */
    @Query("""
            SELECT COUNT(c) > 0
            FROM Candidat c
            WHERE LOWER(c.nom) = LOWER(:nom)
              AND LOWER(c.prenom) = LOWER(:prenom)
              AND c.election.id = :electionId
            """)
    boolean existsByNomAndPrenomAndElectionId(
            @Param("nom") String nom,
            @Param("prenom") String prenom,
            @Param("electionId") Long electionId
    );

    /**
     * Retrieves candidates whose party/affiliation matches the given keyword (case-insensitive).
     * Useful for filtering or searching within large candidate lists.
     *
     * @param affiliation keyword to search in affiliationOuParti
     * @param electionId  the election to search within
     * @return matching candidates
     */
    @Query("""
            SELECT c FROM Candidat c
            WHERE c.election.id = :electionId
              AND LOWER(c.affiliationOuParti) LIKE LOWER(CONCAT('%', :affiliation, '%'))
            ORDER BY c.nom ASC
            """)
    List<Candidat> findByElectionIdAndAffiliationContaining(
            @Param("electionId") Long electionId,
            @Param("affiliation") String affiliation
    );

    /**
     * Deletes all candidates associated with a specific election.
     * Used when an election in BROUILLON state is reset or deleted.
     *
     * @param electionId the ID of the election
     */
    void deleteByElectionId(Long electionId);
}
