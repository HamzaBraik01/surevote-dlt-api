package ma.youcode.surevote.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.domain.entity.Election;
import ma.youcode.surevote.domain.enums.StatutElection;
import ma.youcode.surevote.repository.ElectionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service responsible for scheduled election state machine transitions.
 *
 * The election lifecycle is automated via this scheduler running every 60 seconds:
 *   BROUILLON  → PLANIFIEE  (manual admin action only)
 *   PLANIFIEE  → OUVERTE    (when date_debut is reached)
 *   OUVERTE    → CLOTUREE   (when date_fin is reached)
 *   CLOTUREE   → PUBLIEE    (manual admin action only)
 *
 * IMP-1: Uses targeted queries (findElectionsToOpen/Close) instead of loading
 * all elections by status and filtering in Java.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ElectionSchedulerService {

    private final ElectionRepository electionRepository;

    /**
     * Runs every 60 seconds to transition elections between states
     * based on the current time and their configured start/end dates.
     *
     * Uses targeted repository queries that push the date filtering to the DB,
     * only returning elections that actually need transitioning.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void updateElectionStatuses() {
        log.debug("Election scheduler task started");
        LocalDateTime now = LocalDateTime.now();

        try {
            openScheduledElections(now);
            closeScheduledElections(now);
            log.debug("Election scheduler task completed successfully");
        } catch (Exception e) {
            log.error("Error updating election statuses", e);
        }
    }

    /**
     * IMP-1: Uses findElectionsToOpen(now) — only loads elections
     * that are PLANIFIEE AND whose dateDebut <= now.
     */
    private void openScheduledElections(LocalDateTime now) {
        List<Election> electionsToOpen = electionRepository.findElectionsToOpen(now);

        for (Election election : electionsToOpen) {
            election.setStatut(StatutElection.OUVERTE);
            electionRepository.save(election);
            log.info("Election transitioned to OUVERTE: id={}, titre='{}'",
                election.getId(), election.getTitre());
        }
    }

    /**
     * IMP-1: Uses findElectionsToClose(now) — only loads elections
     * that are OUVERTE AND whose dateFin <= now.
     */
    private void closeScheduledElections(LocalDateTime now) {
        List<Election> electionsToClose = electionRepository.findElectionsToClose(now);

        for (Election election : electionsToClose) {
            election.setStatut(StatutElection.CLOTUREE);
            electionRepository.save(election);
            log.info("Election transitioned to CLOTUREE: id={}, titre='{}'",
                election.getId(), election.getTitre());
        }
    }
}
