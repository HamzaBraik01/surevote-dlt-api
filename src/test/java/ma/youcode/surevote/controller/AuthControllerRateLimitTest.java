package ma.youcode.surevote.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.youcode.surevote.dto.request.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Integration tests for AuthController rate limiting.
 * Uses H2 in-memory DB (test profile).
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthControllerRateLimitTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.springframework.beans.factory.annotation.Autowired
    private WebApplicationContext webApplicationContext;

    @org.junit.jupiter.api.BeforeEach
    void setupMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void login_after6Attempts_returns429() throws Exception {
        LoginRequest request = new LoginRequest("nonexistent@test.ma", "wrongpassword");
        String body = objectMapper.writeValueAsString(request);

        // First 5 attempts: 401 (bad credentials) — rate limit not yet hit
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .header("X-Forwarded-For", "10.0.0.99"))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt from same IP: 429 Too Many Requests
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("X-Forwarded-For", "10.0.0.99"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void login_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
