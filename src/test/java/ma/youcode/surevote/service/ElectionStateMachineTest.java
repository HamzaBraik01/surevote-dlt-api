package ma.youcode.surevote.service;

import ma.youcode.surevote.domain.entity.*;
import ma.youcode.surevote.domain.enums.StatutElection;
import ma.youcode.surevote.exception.InvalidElectionStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Election entity state machine transitions.
 * No Spring context needed — pure domain logic.
 */
class ElectionStateMachineTest {

    private Election election;

    @BeforeEach
    void setUp() {
        election = Election.builder()
                .titre("Test Election")
                .dateDebut(LocalDateTime.now().plusHours(1))
                .dateFin(LocalDateTime.now().plusHours(5))
                .statut(StatutElection.BROUILLON)
                .build();
    }

    @Test
    void planifier_fromBrouillon_succeeds() {
        election.planifier();
        assertThat(election.getStatut()).isEqualTo(StatutElection.PLANIFIEE);
    }

    @Test
    void planifier_fromOuverte_throws() {
        election.planifier();
        election.ouvrir();
        assertThatThrownBy(election::planifier)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ouvrir_fromPlanifiee_succeeds() {
        election.planifier();
        election.ouvrir();
        assertThat(election.isOuverte()).isTrue();
    }

    @Test
    void cloturer_fromOuverte_succeeds() {
        election.planifier();
        election.ouvrir();
        election.cloturer();
        assertThat(election.isCloturee()).isTrue();
    }

    @Test
    void publier_fromCloturee_succeeds() {
        election.planifier();
        election.ouvrir();
        election.cloturer();
        election.publier();
        assertThat(election.isPubliee()).isTrue();
    }

    @Test
    void fullLifecycle_isCorrect() {
        assertThat(election.getStatut()).isEqualTo(StatutElection.BROUILLON);
        election.planifier();
        assertThat(election.getStatut()).isEqualTo(StatutElection.PLANIFIEE);
        election.ouvrir();
        assertThat(election.getStatut()).isEqualTo(StatutElection.OUVERTE);
        election.cloturer();
        assertThat(election.getStatut()).isEqualTo(StatutElection.CLOTUREE);
        election.publier();
        assertThat(election.getStatut()).isEqualTo(StatutElection.PUBLIEE);
    }

    @Test
    void skipState_fromBrouillonToOuverte_throws() {
        assertThatThrownBy(election::ouvrir)
                .isInstanceOf(IllegalStateException.class);
    }
}
