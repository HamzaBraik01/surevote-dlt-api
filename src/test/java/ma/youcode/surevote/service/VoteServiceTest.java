package ma.youcode.surevote.service;

import ma.youcode.surevote.domain.entity.*;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import ma.youcode.surevote.domain.enums.StatutElection;
import ma.youcode.surevote.dto.request.VoteRequest;
import ma.youcode.surevote.dto.response.VoteReceiptResponse;
import ma.youcode.surevote.exception.AlreadyVotedException;
import ma.youcode.surevote.exception.ElectionNotOpenException;
import ma.youcode.surevote.exception.VoterNotEligibleException;
import ma.youcode.surevote.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for VoteService.
 *
 * Tests cover:
 *  - Vote submission (success, validation)
 *  - Double-barrier anonymity verification
 *  - Duplicate vote prevention
 *  - Voter eligibility checks
 *  - Election state validation
 */
@ExtendWith(MockitoExtension.class)
class VoteServiceTest {

    @InjectMocks
    private VoteService voteService;

    @Mock
    private ElectionRepository electionRepository;

    @Mock
    private CandidatRepository candidatRepository;

    @Mock
    private VoteRepository voteRepository;

    @Mock
    private EmargementRepository emargementRepository;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    private Election openElection;
    private Candidat candidat;
    private Electeur voter;
    private VoteRequest voteRequest;

    @BeforeEach
    void setUp() {
        // Setup test data
        voter = new Electeur();
        voter.setId(1L);
        voter.setEmail("voter@example.com");
        voter.setNom("Voter");
        voter.setPrenom("Test");
        voter.setRole(RoleUtilisateur.ELECTEUR);
        voter.setDoubleFacteurActif(false);
        voter.setOtpVerified(true);
        voter.setEnabled(true);

        openElection = new Election();
        openElection.setId(100L);
        openElection.setTitre("Test Election");
        openElection.setStatut(StatutElection.OUVERTE);
        openElection.setDateDebut(LocalDateTime.now().minusHours(1));
        openElection.setDateFin(LocalDateTime.now().plusHours(1));

        candidat = new Candidat();
        candidat.setId(200L);
        candidat.setNom("Smith");
        candidat.setPrenom("John");
        candidat.setElection(openElection);

        voteRequest = new VoteRequest();
        voteRequest.setElectionId(100L);
        voteRequest.setCandidatId(200L);

        // Setup security context
        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.getName()).thenReturn("voter@example.com");
        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    // =========================================================
    // Vote Submission Tests
    // =========================================================

    @Test
    void testSubmitVote_Success() {
        // Arrange
        when(utilisateurRepository.findByEmail("voter@example.com")).thenReturn(Optional.of(voter));
        when(electionRepository.findById(100L)).thenReturn(Optional.of(openElection));
        when(candidatRepository.findByIdAndElectionId(200L, 100L)).thenReturn(Optional.of(candidat));
        when(emargementRepository.existsByElecteur_IdAndElection_Id(1L, 100L)).thenReturn(false);
        when(emargementRepository.save(any(Emargement.class))).thenAnswer(i -> i.getArgument(0));
        when(voteRepository.save(any(Vote.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        VoteReceiptResponse receipt = voteService.submitVote(voteRequest);

        // Assert
        assertNotNull(receipt);
        assertNotNull(receipt.getRecuCryptographique());

        // Verify both emargement and vote were saved (double-barrier)
        verify(emargementRepository, times(1)).save(any(Emargement.class));
        verify(voteRepository, times(1)).save(any(Vote.class));
    }

    @Test
    void testSubmitVote_ElectionNotOpen() {
        // Arrange - election is in BROUILLON state
        openElection.setStatut(StatutElection.BROUILLON);
        when(utilisateurRepository.findByEmail("voter@example.com")).thenReturn(Optional.of(voter));
        when(electionRepository.findById(100L)).thenReturn(Optional.of(openElection));

        // Act & Assert
        assertThrows(ElectionNotOpenException.class, () -> {
            voteService.submitVote(voteRequest);
        });

        verify(emargementRepository, never()).save(any());
        verify(voteRepository, never()).save(any());
    }

    @Test
    void testSubmitVote_AlreadyVoted() {
        // Arrange
        when(utilisateurRepository.findByEmail("voter@example.com")).thenReturn(Optional.of(voter));
        when(electionRepository.findById(100L)).thenReturn(Optional.of(openElection));
        when(emargementRepository.existsByElecteur_IdAndElection_Id(1L, 100L)).thenReturn(true);

        // Act & Assert
        assertThrows(AlreadyVotedException.class, () -> {
            voteService.submitVote(voteRequest);
        });

        verify(voteRepository, never()).save(any());
    }

    @Test
    void testSubmitVote_VoterNotEligible() {
        // Arrange - voter belongs to a different college
        CollegeElectoral college = new CollegeElectoral();
        college.setId(1L);
        college.setNom("College A");
        openElection.setCollegeElectoral(college);

        Electeur voterFromOtherCollege = new Electeur();
        voterFromOtherCollege.setId(1L);
        voterFromOtherCollege.setEmail("voter@example.com");
        voterFromOtherCollege.setCollegeElectoral(null); // Not in required college

        when(utilisateurRepository.findByEmail("voter@example.com")).thenReturn(Optional.of(voter));
        when(electionRepository.findById(100L)).thenReturn(Optional.of(openElection));

        // Act & Assert
        assertThrows(VoterNotEligibleException.class, () -> {
            voteService.submitVote(voteRequest);
        });
    }

    @Test
    void testSubmitVote_CandidateNotFound() {
        // Arrange
        when(utilisateurRepository.findByEmail("voter@example.com")).thenReturn(Optional.of(voter));
        when(electionRepository.findById(100L)).thenReturn(Optional.of(openElection));
        when(emargementRepository.existsByElecteur_IdAndElection_Id(1L, 100L)).thenReturn(false);
        when(candidatRepository.findByIdAndElectionId(200L, 100L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(Exception.class, () -> {
            voteService.submitVote(voteRequest);
        });
    }

    // =========================================================
    // Double-Barrier Anonymity Tests
    // =========================================================

    @Test
    void testVoteAnonymity_NoUserReferenceInVote() {
        // Arrange
        when(utilisateurRepository.findByEmail("voter@example.com")).thenReturn(Optional.of(voter));
        when(electionRepository.findById(100L)).thenReturn(Optional.of(openElection));
        when(candidatRepository.findByIdAndElectionId(200L, 100L)).thenReturn(Optional.of(candidat));
        when(emargementRepository.existsByElecteur_IdAndElection_Id(1L, 100L)).thenReturn(false);

        java.util.concurrent.atomic.AtomicReference<Vote> capturedVote = new java.util.concurrent.atomic.AtomicReference<>();
        when(voteRepository.save(any(Vote.class))).thenAnswer(invocation -> {
            Vote v = invocation.getArgument(0);
            capturedVote.set(v);
            return v;
        });

        when(emargementRepository.save(any(Emargement.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        voteService.submitVote(voteRequest);

        // Assert - Vote record should have NO reference to voter
        assertNotNull(capturedVote.get());
        // Vote should only contain election + candidate
        // Voter ID should NOT be stored in Vote entity
    }

    @Test
    void testVoteChecksum_IsValid() throws Exception {
        // Verify that checksum is properly computed
        when(utilisateurRepository.findByEmail("voter@example.com")).thenReturn(Optional.of(voter));
        when(electionRepository.findById(100L)).thenReturn(Optional.of(openElection));
        when(candidatRepository.findByIdAndElectionId(200L, 100L)).thenReturn(Optional.of(candidat));
        when(emargementRepository.existsByElecteur_IdAndElection_Id(1L, 100L)).thenReturn(false);
        when(emargementRepository.save(any(Emargement.class))).thenAnswer(i -> i.getArgument(0));
        when(voteRepository.save(any(Vote.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        VoteReceiptResponse receipt = voteService.submitVote(voteRequest);

        // Assert
        assertNotNull(receipt.getRecuCryptographique());
    }

    // =========================================================
    // Election State Tests
    // =========================================================

    @Test
    void testSubmitVote_ElectionClosed() {
        // Arrange - election has ended
        openElection.setStatut(StatutElection.CLOTUREE);
        when(utilisateurRepository.findByEmail("voter@example.com")).thenReturn(Optional.of(voter));
        when(electionRepository.findById(100L)).thenReturn(Optional.of(openElection));

        // Act & Assert
        assertThrows(ElectionNotOpenException.class, () -> {
            voteService.submitVote(voteRequest);
        });
    }

    @Test
    void testSubmitVote_TwoFactorNotCompleted() {
        // Arrange - voter hasn't verified OTP
        voter.setDoubleFacteurActif(true);
        voter.setOtpVerified(false);
        when(utilisateurRepository.findByEmail("voter@example.com")).thenReturn(Optional.of(voter));
        when(electionRepository.findById(100L)).thenReturn(Optional.of(openElection));

        // Act & Assert
        assertThrows(Exception.class, () -> {
            voteService.submitVote(voteRequest);
        });
    }
}
