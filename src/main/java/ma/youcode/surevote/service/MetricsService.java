package ma.youcode.surevote.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import ma.youcode.surevote.domain.enums.StatutElection;
import ma.youcode.surevote.dto.response.MetricsResponse;
import ma.youcode.surevote.repository.CollegeElectoralRepository;
import ma.youcode.surevote.repository.ElectionRepository;
import ma.youcode.surevote.repository.EmargementRepository;
import ma.youcode.surevote.repository.LogAuditRepository;
import ma.youcode.surevote.repository.UtilisateurRepository;
import ma.youcode.surevote.repository.VoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service responsible for computing aggregated, platform-wide statistics
 * exposed via the Observer metrics dashboard (/api/observer/metrics).
 *
 * All metrics are strictly anonymized:
 *  - No voter identity is exposed.
 *  - No ballot content is exposed.
 *  - All figures are aggregated counts and rates.
 *
 * Access: ADMIN and OBSERVATEUR roles only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MetricsService {

    private final UtilisateurRepository utilisateurRepository;
    private final ElectionRepository electionRepository;
    private final VoteRepository voteRepository;
    private final EmargementRepository emargementRepository;
    private final CollegeElectoralRepository collegeElectoralRepository;
    private final LogAuditRepository logAuditRepository;

    // =========================================================
    // Main aggregation method
    // =========================================================

    /**
     * Computes and returns the full platform metrics snapshot.
     *
     * Aggregates data from all repositories into a single MetricsResponse DTO.
     * The computation is deterministic and based on the current state of the database
     * at the time of the request.
     *
     * @return a fully populated MetricsResponse DTO
     */
    public MetricsResponse computeFullMetrics() {
        log.debug("Computing full platform metrics...");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last24h = now.minusHours(24);

        return MetricsResponse.builder()
                .computedAt(now)

                // --- User statistics ---
                .totalUtilisateurs(computeTotalUsers())
                .totalElecteurs(utilisateurRepository.countByRole(RoleUtilisateur.ELECTEUR))
                .totalAdministrateurs(utilisateurRepository.countByRole(RoleUtilisateur.ADMIN))
                .totalObservateurs(utilisateurRepository.countByRole(RoleUtilisateur.OBSERVATEUR))
                .totalComptesActifs(utilisateurRepository.findAllByIsEnabledTrue().size())
                .totalComptesDesactives(utilisateurRepository.findAllByIsEnabledFalse().size())

                // --- Election statistics ---
                .totalElections(electionRepository.count())
                .electionsBrouillon(electionRepository.countByStatut(StatutElection.BROUILLON))
                .electionsPlannifiees(electionRepository.countByStatut(StatutElection.PLANIFIEE))
                .electionsOuvertes(electionRepository.countByStatut(StatutElection.OUVERTE))
                .electionsCloturees(electionRepository.countByStatut(StatutElection.CLOTUREE))
                .electionsPubliees(electionRepository.countByStatut(StatutElection.PUBLIEE))
                .totalColleges(collegeElectoralRepository.count())

                // --- Vote statistics ---
                .totalVotesCastes(voteRepository.countAllVotes())
                .totalEmargements(emargementRepository.count())
                .tauxParticipationMoyen(computeAverageParticipationRate())
                .electionsAvecVotes(voteRepository.countDistinctElectionsWithVotes())

                // --- Per-election participation summary ---
                .participationParElection(buildParticipationSummaries())

                // --- Audit statistics ---
                .totalLogsAudit(logAuditRepository.count())
                .logsAuditDernieres24h(logAuditRepository.countByDateActionAfter(last24h))
                .echecConnexionDernieres24h(computeLoginFailures24h(last24h))
                .totalTentativesFraude(computeFraudAttempts())
                .distributionActionsAudit(buildAuditActionDistribution())

                .build();
    }

    /**
     * Returns a lightweight summary of platform health metrics.
     * Faster to compute than the full metrics — suitable for dashboard header stats.
     *
     * @return a partial MetricsResponse with only the most critical counters
     */
    public MetricsResponse computeSummaryMetrics() {
        log.debug("Computing summary metrics...");
        LocalDateTime now = LocalDateTime.now();

        return MetricsResponse.builder()
                .computedAt(now)
                .totalUtilisateurs(computeTotalUsers())
                .totalElecteurs(utilisateurRepository.countByRole(RoleUtilisateur.ELECTEUR))
                .totalElections(electionRepository.count())
                .electionsOuvertes(electionRepository.countByStatut(StatutElection.OUVERTE))
                .electionsPubliees(electionRepository.countByStatut(StatutElection.PUBLIEE))
                .totalVotesCastes(voteRepository.countAllVotes())
                .tauxParticipationMoyen(computeAverageParticipationRate())
                .build();
    }

    // =========================================================
    // Individual metric computations
    // =========================================================

    /**
     * Computes the average participation rate across all closed and published elections.
     *
     * For each closed/published election:
     *  - Counts the emargements (participations) for that election.
     *  - Divides by the total eligible voters (college size or global ELECTEUR count).
     * Returns the mean of all individual rates, or 0.0 if no elections qualify.
     *
     * @return average participation rate as a percentage (0.0 to 100.0)
     */
    public double computeAverageParticipationRate() {
        // Single aggregated query instead of N+1 per election
        List<Object[]> rows = emargementRepository.countParticipantsGroupedByClosedElection();
        if (rows.isEmpty()) return 0.0;

        double sumRates = 0.0;
        int count = 0;

        for (Object[] row : rows) {
            Long electionId = (Long) row[0];
            long participants = (Long) row[1];

            // Use the correct denominator: college size if restricted, else global count
            ma.youcode.surevote.domain.entity.Election election =
                electionRepository.findById(electionId).orElse(null);
            if (election == null) continue;

            long eligibles;
            if (election.getCollegeElectoral() != null) {
                eligibles = collegeElectoralRepository
                        .countMembersByCollegeId(election.getCollegeElectoral().getId());
            } else {
                eligibles = utilisateurRepository.countByRole(RoleUtilisateur.ELECTEUR);
            }

            if (eligibles > 0) {
                sumRates += ((double) participants / eligibles) * 100.0;
                count++;
            }
        }

        return count > 0 ? Math.round((sumRates / count) * 100.0) / 100.0 : 0.0;
    }

    /**
     * Counts the number of LOGIN_FAILURE events in the last 24 hours.
     * Used to assess brute-force activity.
     *
     * @param since the start of the 24-hour window
     * @return count of login failures
     */
    public long computeLoginFailures24h(LocalDateTime since) {
        try {
            return logAuditRepository.countLoginFailuresByIp("", since);
        } catch (Exception e) {
            log.warn("Could not compute login failures metric: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * Counts the total number of fraud attempt events in the audit log.
     * Includes: FRAUD_ATTEMPT, DUPLICATE_VOTE_ATTEMPT, UNAUTHORIZED_ACCESS.
     *
     * @return total fraud-related audit entries
     */
    public long computeFraudAttempts() {
        try {
            return logAuditRepository.findFraudAttempts(
                    org.springframework.data.domain.PageRequest.of(0, 1)
            ).getTotalElements();
        } catch (Exception e) {
            log.warn("Could not compute fraud attempts metric: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * Builds the per-election participation summary list.
     * Only includes elections that are CLOTUREE or PUBLIEE.
     * Ordered by election dateFin descending (most recently closed first).
     *
     * @return list of ElectionParticipationSummary DTOs
     */
    public List<MetricsResponse.ElectionParticipationSummary> buildParticipationSummaries() {
        long totalElecteurs = utilisateurRepository.countByRole(RoleUtilisateur.ELECTEUR);

        return electionRepository.findAll()
                .stream()
                .filter(e -> e.getStatut() == StatutElection.CLOTUREE
                          || e.getStatut() == StatutElection.PUBLIEE)
                .sorted((a, b) -> b.getDateFin().compareTo(a.getDateFin()))
                .map(election -> {
                    long participants = emargementRepository.countByElection_Id(election.getId());
                    long votes       = voteRepository.countByElectionId(election.getId());

                    // Eligible voters: college size if restricted, otherwise global count
                    long eligibles;
                    if (election.getCollegeElectoral() != null) {
                        eligibles = collegeElectoralRepository
                                .countMembersByCollegeId(election.getCollegeElectoral().getId());
                    } else {
                        eligibles = totalElecteurs;
                    }

                    double tauxParticipation = (eligibles > 0)
                            ? Math.round(((double) participants / eligibles) * 10000.0) / 100.0
                            : 0.0;

                    int totalCandidats = election.getCandidats() != null
                            ? election.getCandidats().size()
                            : 0;

                    return MetricsResponse.ElectionParticipationSummary.builder()
                            .electionId(election.getId())
                            .titre(election.getTitre())
                            .statut(election.getStatut().name())
                            .totalVotes(votes)
                            .totalElecteursEligibles(eligibles)
                            .tauxParticipation(tauxParticipation)
                            .totalCandidats(totalCandidats)
                            .dateFin(election.getDateFin())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Builds a distribution map of audit log entries grouped by action type.
     * Example: { "LOGIN_SUCCESS": 1240, "VOTE_SUBMITTED": 380, ... }
     *
     * @return map of actionType → count
     */
    public Map<String, Long> buildAuditActionDistribution() {
        Map<String, Long> distribution = new HashMap<>();
        try {
            List<Object[]> rawCounts = logAuditRepository.countByActionType();
            for (Object[] row : rawCounts) {
                String actionType = (String) row[0];
                Long count        = (Long) row[1];
                if (actionType != null) {
                    distribution.put(actionType, count);
                }
            }
        } catch (Exception e) {
            log.warn("Could not build audit action distribution: {}", e.getMessage());
        }
        return distribution;
    }

    // =========================================================
    // Helper methods
    // =========================================================

    /**
     * Returns the total number of registered users across all roles.
     *
     * @return total user count
     */
    private long computeTotalUsers() {
        return utilisateurRepository.count();
    }

    /**
     * Returns raw election data for CLOTUREE and PUBLIEE elections.
     * Used for average participation rate computation.
     *
     * @return list of raw row data with election IDs
     */
    private List<Object[]> buildClosedElectionRawData() {
        return electionRepository.findAll()
                .stream()
                .filter(e -> e.getStatut() == StatutElection.CLOTUREE
                          || e.getStatut() == StatutElection.PUBLIEE)
                .map(e -> new Object[]{ e.getId() })
                .collect(Collectors.toList());
    }
}
