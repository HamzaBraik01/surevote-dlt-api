package ma.youcode.surevote.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class PublicEndpointsIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void listPublicElections_returns200() throws Exception {
        mockMvc.perform(get("/api/elections"))
                .andExpect(status().isOk());
    }

    @Test
    void receiptExists_publicEndpoint_returns200WithSchema() throws Exception {
        mockMvc.perform(get("/public/verify/00000000-0000-0000-0000-000000000000/exists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").exists())
                .andExpect(jsonPath("$.uuid").value("00000000-0000-0000-0000-000000000000"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void adminUsers_withoutAuth_isForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }
}

