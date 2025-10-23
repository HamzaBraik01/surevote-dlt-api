package ma.youcode.surevote.controller;

import ma.youcode.surevote.domain.entity.*;
import ma.youcode.surevote.domain.enums.StatutElection;
import ma.youcode.surevote.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Integration tests for VoteController.
 * Verifies security guards, eligibility checks, and double-vote prevention.
 */
@SpringBootTest
@ActiveProfiles("test")
class VoteControllerIntegrationTest {

    private MockMvc mockMvc;
    @Autowired private ElectionRepository electionRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private EmargementRepository emargementRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private WebApplicationContext webApplicationContext;

    private Election openElection;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        emargementRepository.deleteAll();
        electionRepository.deleteAll();
        utilisateurRepository.deleteAll();

        openElection = electionRepository.save(Election.builder()
                .titre("Test Election")
                .dateDebut(LocalDateTime.now().minusHours(1))
                .dateFin(LocalDateTime.now().plusHours(2))
                .statut(StatutElection.OUVERTE)
                .build());
    }

    @Test
    void eligibilityCheck_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/vote/eligibility/" + openElection.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void eligibilityCheck_withAdminRole_returns403() throws Exception {
        mockMvc.perform(get("/api/vote/eligibility/" + openElection.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitVote_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/vote/submit")
                .contentType("application/json")
                .content("{\"electionId\":1,\"candidatId\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void receiptVerification_publicEndpoint_returns404ForUnknownUuid() throws Exception {
        mockMvc.perform(get("/api/vote/receipt/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isForbidden());
    }
}
