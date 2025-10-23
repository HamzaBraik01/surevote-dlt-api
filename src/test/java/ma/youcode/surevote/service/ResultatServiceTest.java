package ma.youcode.surevote.service;

import ma.youcode.surevote.domain.entity.Candidat;
import ma.youcode.surevote.domain.entity.CollegeElectoral;
import ma.youcode.surevote.domain.entity.Election;
import ma.youcode.surevote.domain.entity.Vote;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import ma.youcode.surevote.domain.enums.StatutElection;
import ma.youcode.surevote.dto.response.ResultatResponse;
import ma.youcode.surevote.exception.ResourceNotFoundException;
import ma.youcode.surevote.exception.ResultsNotAvailableException;
import ma.youcode.surevote.repository.CandidatRepository;
import ma.youcode.surevote.repository.CollegeElectoralRepository;
import ma.youcode.surevote.repository.ElectionRepository;
import ma.youcode.surevote.repository.EmargementRepository;
import ma.youcode.surevote.repository.UtilisateurRepository;
import ma.youcode.surevote.repository.VoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultatServiceTest {

    @InjectMocks
    private ResultatService resultatService;

    @Mock private ElectionRepository electionRepository;
    @Mock private CandidatRepository candidatRepository;
    @Mock private VoteRepository voteRepository;
    @Mock private EmargementRepository emargementRepository;
    @Mock private CollegeElectoralRepository collegeElectoralRepository;
    @Mock private UtilisateurRepository utilisateurRepository;

    private Election publishedElection;
    private Election closedElection;
    private Election draftElection;

    @BeforeEach
    void setUp() {
        publishedElection = Election.builder()
                .id(1L)
                .titre("Publiee")
                .description("desc")
                .dateDebut(LocalDateTime.now().minusDays(2))
                .dateFin(LocalDateTime.now().minusDays(1))
                .statut(StatutElection.PUBLIEE)
                .build();

        closedElection = Election.builder()
                .id(2L)
                .titre("Cloturee")
                .description("desc")
                .dateDebut(LocalDateTime.now().minusDays(2))
                .dateFin(LocalDateTime.now().minusDays(1))
                .statut(StatutElection.CLOTUREE)
                .build();

        draftElection = Election.builder()
                .id(3L)
                .titre("Draft")
                .description("desc")
                .dateDebut(LocalDateTime.now().plusDays(2))
                .dateFin(LocalDateTime.now().plusDays(3))
                .statut(StatutElection.BROUILLON)
                .build();
    }

    @Test
    void getPublicResults_success_whenPublished() {
        Candidat c1 = Candidat.builder().id(11L).nom("A").prenom("Ali").affiliationOuParti("P1").build();
        Candidat c2 = Candidat.builder().id(12L).nom("B").prenom("Badr").affiliationOuParti("P2").build();

        when(electionRepository.findByIdWithCollegeElectoral(1L)).thenReturn(Optional.of(publishedElection));

        when(candidatRepository.findCandidatsWithVoteCount(1L)).thenReturn(List.of(
                new Object[]{c1, 8L},
                new Object[]{c2, 4L}
        ));
        when(voteRepository.countByElectionId(1L)).thenReturn(12L);
        when(utilisateurRepository.countByRole(RoleUtilisateur.ELECTEUR)).thenReturn(20L);
        when(electionRepository.existsById(1L)).thenReturn(true);
        when(voteRepository.findVoteIdAndChecksumByElectionId(1L)).thenReturn(List.of(
                new Object[]{101L, "abc"},
                new Object[]{102L, "def"}
        ));

        ResultatResponse response = resultatService.getPublicResults(1L);

        assertThat(response).isNotNull();
        assertThat(response.getElectionId()).isEqualTo(1L);
        assertThat(response.getTotalVotes()).isEqualTo(12L);
        assertThat(response.getResultats()).hasSize(2);
        assertThat(response.getGagnant()).isNotNull();
        assertThat(response.getTauxParticipation()).isEqualTo(60.0);
    }

    @Test
    void getPublicResults_throws_whenNotPublished() {
        when(electionRepository.findByIdWithCollegeElectoral(2L)).thenReturn(Optional.of(closedElection));
        assertThrows(ResultsNotAvailableException.class, () -> resultatService.getPublicResults(2L));
    }

    @Test
    void getResultsForAdmin_success_whenClosed() {
        Candidat c1 = Candidat.builder().id(21L).nom("C").prenom("Cha").affiliationOuParti("P3").build();
        Candidat c2 = Candidat.builder().id(22L).nom("D").prenom("Dia").affiliationOuParti("P4").build();

        CollegeElectoral college = CollegeElectoral.builder().id(99L).nom("Info").build();
        closedElection.setCollegeElectoral(college);

        when(electionRepository.findByIdWithCollegeElectoral(2L)).thenReturn(Optional.of(closedElection));

        when(candidatRepository.findCandidatsWithVoteCount(2L)).thenReturn(List.of(
                new Object[]{c1, 5L},
                new Object[]{c2, 5L}
        ));
        when(voteRepository.countByElectionId(2L)).thenReturn(10L);
        when(collegeElectoralRepository.countMembersByCollegeId(99L)).thenReturn(25L);
        when(electionRepository.existsById(2L)).thenReturn(true);
        when(voteRepository.findVoteIdAndChecksumByElectionId(2L))
                .thenReturn(java.util.Collections.singletonList(new Object[]{201L, "x"}));

        ResultatResponse response = resultatService.getResultsForAdmin(2L);

        assertThat(response).isNotNull();
        assertThat(response.isEgalite()).isTrue();
        assertThat(response.getTauxParticipation()).isEqualTo(40.0);
    }

    @Test
    void getResultsForAdmin_throws_whenStatusInvalid() {
        when(electionRepository.findByIdWithCollegeElectoral(3L)).thenReturn(Optional.of(draftElection));
        assertThrows(ResultsNotAvailableException.class, () -> resultatService.getResultsForAdmin(3L));
    }

    @Test
    void receiptExists_delegatesToRepository() {
        when(emargementRepository.existsByRecuCryptographique("r-1")).thenReturn(true);
        assertThat(resultatService.receiptExists("r-1")).isTrue();
    }

    @Test
    void computeVoteTableChecksum_throws_whenElectionMissing() {
        when(electionRepository.existsById(404L)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> resultatService.computeVoteTableChecksum(404L));
    }

    @Test
    void computeVoteTableChecksum_returnsDeterministicHash() {
        when(electionRepository.existsById(8L)).thenReturn(true);
        when(voteRepository.findVoteIdAndChecksumByElectionId(8L)).thenReturn(List.of(
                new Object[]{1L, "aa"},
                new Object[]{2L, null}
        ));

        String actual = resultatService.computeVoteTableChecksum(8L);
        String expected = sha256("election=8;count=2;v1:aa;v2:null;");

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void computeVoteChecksum_usesSaltAndFields() {
        Election e = Election.builder().id(77L).build();
        Candidat c = Candidat.builder().id(99L).build();
        Vote v = Vote.builder().election(e).candidat(c).checksumSalt("salt-1").build();

        String actual = resultatService.computeVoteChecksum(v);
        String expected = sha256("election=77|candidat=99|salt=salt-1");

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void detectTamperedVotes_returnsMismatchedVoteIds_only() {
        Election e = Election.builder().id(10L).build();
        Candidat c = Candidat.builder().id(20L).build();

        Vote valid = Vote.builder()
                .id(1L)
                .election(e)
                .candidat(c)
                .checksumSalt("s1")
                .checksum(sha256("election=10|candidat=20|salt=s1"))
                .build();

        Vote tampered = Vote.builder()
                .id(2L)
                .election(e)
                .candidat(c)
                .checksumSalt("s2")
                .checksum("invalid-checksum")
                .build();

        Vote skipped = Vote.builder()
                .id(3L)
                .election(e)
                .candidat(c)
                .checksumSalt("s3")
                .checksum(null)
                .build();

        when(voteRepository.findAllByElectionIdOrderById(10L)).thenReturn(List.of(valid, tampered, skipped));

        List<Long> tamperedIds = resultatService.detectTamperedVotes(10L);
        assertThat(tamperedIds).containsExactly(2L);
    }

    @Test
    void countMethods_and_participationRate_work() {
        when(voteRepository.countByElectionId(1L)).thenReturn(7L);
        when(emargementRepository.countByElection_Id(1L)).thenReturn(5L);
        when(electionRepository.findByIdWithCollegeElectoral(1L)).thenReturn(Optional.of(publishedElection));
        when(utilisateurRepository.countByRole(RoleUtilisateur.ELECTEUR)).thenReturn(10L);

        assertThat(resultatService.countVotesByElection(1L)).isEqualTo(7L);
        assertThat(resultatService.countParticipantsByElection(1L)).isEqualTo(5L);
        assertThat(resultatService.computeParticipationRate(1L)).isEqualTo(50.0);
    }

    @Test
    void computeParticipationRate_returnsZero_whenNoEligible() {
        CollegeElectoral college = CollegeElectoral.builder().id(500L).nom("Zero").build();
        Election restricted = Election.builder()
                .id(50L)
                .titre("Restricted")
                .dateDebut(LocalDateTime.now().minusDays(1))
                .dateFin(LocalDateTime.now())
                .statut(StatutElection.CLOTUREE)
                .collegeElectoral(college)
                .build();

        when(electionRepository.findByIdWithCollegeElectoral(50L)).thenReturn(Optional.of(restricted));
        when(collegeElectoralRepository.countMembersByCollegeId(500L)).thenReturn(0L);

        assertThat(resultatService.computeParticipationRate(50L)).isEqualTo(0.0);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
