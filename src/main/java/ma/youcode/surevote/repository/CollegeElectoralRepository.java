package ma.youcode.surevote.repository;

import ma.youcode.surevote.domain.entity.CollegeElectoral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for CollegeElectoral entities.
 * Provides CRUD operations and custom queries for electoral college management.
 */
@Repository
public interface CollegeElectoralRepository extends JpaRepository<CollegeElectoral, Long> {

    /**
     * Checks whether a college with the given name already exists.
     *
     * @param nom the college name to check
     * @return true if a college with that name exists
     */
    boolean existsByNom(String nom);

    /**
     * Finds a college by its exact name.
     *
     * @param nom the college name
     * @return an Optional containing the college if found
     */
    Optional<CollegeElectoral> findByNom(String nom);

    /**
     * Finds all colleges whose name contains the given string (case-insensitive).
     *
     * @param keyword the search term
     * @return list of matching colleges
     */
    List<CollegeElectoral> findByNomContainingIgnoreCase(String keyword);

    /**
     * Retrieves all colleges that have at least one election assigned to them.
     *
     * @return list of colleges with associated elections
     */
    @Query("SELECT DISTINCT c FROM CollegeElectoral c WHERE c.elections IS NOT EMPTY")
    List<CollegeElectoral> findAllWithElections();

    /**
     * Retrieves the college to which a specific voter (Electeur) belongs.
     *
     * @param electeurId the ID of the voter
     * @return an Optional containing the college if the voter is assigned to one
     */
    @Query("SELECT c FROM CollegeElectoral c JOIN c.electeurs e WHERE e.id = :electeurId")
    Optional<CollegeElectoral> findByElecteurId(@Param("electeurId") Long electeurId);

    /**
     * Returns all colleges along with their member count.
     * Result is a projection: [CollegeElectoral, memberCount].
     *
     * @return list of Object arrays containing [CollegeElectoral, Long memberCount]
     */
    @Query("SELECT c, COUNT(e) FROM CollegeElectoral c LEFT JOIN c.electeurs e GROUP BY c")
    List<Object[]> findAllWithMemberCount();

    /**
     * Counts the number of voters belonging to a specific college.
     *
     * @param collegeId the ID of the college
     * @return the number of voters in that college
     */
    @Query("SELECT COUNT(e) FROM CollegeElectoral c JOIN c.electeurs e WHERE c.id = :collegeId")
    long countMembersByCollegeId(@Param("collegeId") Long collegeId);

    /**
     * Checks whether a specific voter is already a member of a specific college.
     *
     * @param collegeId  the ID of the college
     * @param electeurId the ID of the voter
     * @return true if the voter belongs to the college
     */
    @Query("SELECT COUNT(e) > 0 FROM CollegeElectoral c JOIN c.electeurs e WHERE c.id = :collegeId AND e.id = :electeurId")
    boolean isElecteurInCollege(@Param("collegeId") Long collegeId, @Param("electeurId") Long electeurId);
}
