package ma.youcode.surevote.security;

import ma.youcode.surevote.domain.entity.Electeur;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        // Must be at least 256 bits for HS256
        ReflectionTestUtils.setField(provider, "jwtSecret", "aVeryLongSecretKeyForTestingPurposesThatIs256BitsLong!!");
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", 3600000L);
        ReflectionTestUtils.setField(provider, "refreshExpirationMs", 86400000L);
        ReflectionTestUtils.setField(provider, "jwtIssuer", "surevote-test");
    }

    private Electeur createTestUser() {
        Electeur user = new Electeur();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setNom("Dupont");
        user.setPrenom("Jean");
        user.setRole(RoleUtilisateur.ELECTEUR);
        user.setMotDePasse("hashed");
        user.setCin("AB123456");
        return user;
    }

    @Test
    void generateAccessToken_shouldReturnValidToken() {
        String token = provider.generateAccessToken(createTestUser());
        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    void generateRefreshToken_shouldReturnValidToken() {
        String token = provider.generateRefreshToken(createTestUser());
        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    void getEmailFromToken_shouldReturnCorrectEmail() {
        String token = provider.generateAccessToken(createTestUser());
        String email = provider.getEmailFromToken(token);
        assertThat(email).isEqualTo("test@example.com");
    }

    @Test
    void getUserIdFromToken_shouldReturnCorrectId() {
        String token = provider.generateAccessToken(createTestUser());
        Long id = provider.getUserIdFromToken(token);
        assertThat(id).isEqualTo(1L);
    }

    @Test
    void getRoleFromToken_shouldReturnCorrectRole() {
        String token = provider.generateAccessToken(createTestUser());
        RoleUtilisateur role = provider.getRoleFromToken(token);
        assertThat(role).isEqualTo(RoleUtilisateur.ELECTEUR);
    }

    @Test
    void validateToken_withValidToken_returnsTrue() {
        String token = provider.generateAccessToken(createTestUser());
        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_withMalformedToken_returnsFalse() {
        assertThat(provider.validateToken("not.a.jwt")).isFalse();
    }

    @Test
    void validateToken_withEmptyToken_returnsFalse() {
        assertThat(provider.validateToken("")).isFalse();
    }

    @Test
    void validateToken_withExpiredToken_returnsFalse() {
        // Set expiration to 0 to generate already-expired token
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", 0L);
        String token = provider.generateAccessToken(createTestUser());
        assertThat(provider.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_withWrongSecret_returnsFalse() {
        String token = provider.generateAccessToken(createTestUser());
        // Change the secret
        ReflectionTestUtils.setField(provider, "jwtSecret", "aDifferentSecretKeyThatIsAlso256BitsLongForTesting!!");
        assertThat(provider.validateToken(token)).isFalse();
    }

    @Test
    void getExpirationMs_shouldReturnConfiguredValue() {
        assertThat(provider.getExpirationMs()).isEqualTo(3600000L);
    }

    @Test
    void getExpirationDateFromToken_shouldReturnFutureDate() {
        String token = provider.generateAccessToken(createTestUser());
        Date expiration = provider.getExpirationDateFromToken(token);
        assertThat(expiration).isAfter(new Date());
    }
}
