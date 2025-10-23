package ma.youcode.surevote.service;

import ma.youcode.surevote.domain.entity.Election;
import ma.youcode.surevote.domain.enums.StatutElection;
import ma.youcode.surevote.repository.ElectionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Tests for ElectionSchedulerService using the optimized targeted queries
 * (findElectionsToOpen / findElectionsToClose).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ElectionSchedulerService")
class ElectionSchedulerServiceTest {

    @Mock private ElectionRepository electionRepository;
    @InjectMocks private ElectionSchedulerService service;

    // ── updateElectionStatuses ──────────────────────────────────

    @Test
    @DisplayName("opens scheduled elections returned by findElectionsToOpen")
    void openScheduled_transitionsToOuverte() {
        Election election = Election.builder()
                .id(1L).titre("Test").statut(StatutElection.PLANIFIEE)
                .dateDebut(LocalDateTime.now().minusHours(1))
                .dateFin(LocalDateTime.now().plusHours(1))
                .build();

        when(electionRepository.findElectionsToOpen(any(LocalDateTime.class)))
                .thenReturn(List.of(election));
        when(electionRepository.findElectionsToClose(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(electionRepository.save(any(Election.class))).thenReturn(election);

        service.updateElectionStatuses();

        verify(electionRepository).save(argThat(e -> e.getStatut() == StatutElection.OUVERTE));
    }

    @Test
    @DisplayName("does NOT open elections when findElectionsToOpen returns empty")
    void openScheduled_noElectionsToOpen() {
        when(electionRepository.findElectionsToOpen(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(electionRepository.findElectionsToClose(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        service.updateElectionStatuses();

        verify(electionRepository, never()).save(any());
    }

    @Test
    @DisplayName("closes open elections returned by findElectionsToClose")
    void closeScheduled_transitionsToCloturee() {
        Election election = Election.builder()
                .id(3L).titre("Open").statut(StatutElection.OUVERTE)
                .dateDebut(LocalDateTime.now().minusHours(5))
                .dateFin(LocalDateTime.now().minusHours(1))
                .build();

        when(electionRepository.findElectionsToOpen(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(electionRepository.findElectionsToClose(any(LocalDateTime.class)))
                .thenReturn(List.of(election));
        when(electionRepository.save(any(Election.class))).thenReturn(election);

        service.updateElectionStatuses();

        verify(electionRepository).save(argThat(e -> e.getStatut() == StatutElection.CLOTUREE));
    }

    @Test
    @DisplayName("does NOT close elections when findElectionsToClose returns empty")
    void closeScheduled_noElectionsToClose() {
        when(electionRepository.findElectionsToOpen(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(electionRepository.findElectionsToClose(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        service.updateElectionStatuses();

        verify(electionRepository, never()).save(any());
    }

    @Test
    @DisplayName("handles no elections gracefully")
    void updateStatuses_noElections() {
        when(electionRepository.findElectionsToOpen(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(electionRepository.findElectionsToClose(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        service.updateElectionStatuses();

        verify(electionRepository, never()).save(any());
    }

    @Test
    @DisplayName("exception is caught and logged, not rethrown")
    void updateStatuses_exceptionHandled() {
        when(electionRepository.findElectionsToOpen(any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("DB error"));

        // Should not throw
        service.updateElectionStatuses();
    }
}
