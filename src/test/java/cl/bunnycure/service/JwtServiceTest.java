package cl.bunnycure.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private final String strongSecret = "052f6c36836a61907797277089c48c58b98bb6e0291caff75c72402dde0a592cd05b8fe490013dea30125e52ad4990e8740ea9339879ba6026ae1480930e06bf";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", strongSecret);
        ReflectionTestUtils.setField(jwtService, "accessExpiration", 900000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", 604800000L);
        jwtService.validateSecret();
    }

    @Test
    void validateSecret_ShouldPass_WithStrongSecret() {
        assertDoesNotThrow(() -> jwtService.validateSecret());
    }

    @Test
    void validateSecret_ShouldThrow_WhenSecretTooShort() {
        JwtService weakService = new JwtService();
        ReflectionTestUtils.setField(weakService, "secret", "short-secret-less-than-32-chars");
        assertThrows(IllegalStateException.class, weakService::validateSecret);
    }

    @Test
    void generateToken_AndValidate_ShouldSucceed() {
        UserDetails userDetails = new User("admin", "password", Collections.emptyList());

        String token = jwtService.generateToken(userDetails);
        assertNotNull(token);
        assertTrue(token.length() > 20);

        String username = jwtService.extractUsername(token);
        assertEquals("admin", username);

        assertTrue(jwtService.validateToken(token, userDetails));
        assertTrue(jwtService.validateToken(token));
    }

    @Test
    void generateRefreshToken_ShouldHaveCorrectTokenType() {
        UserDetails userDetails = new User("admin", "password", Collections.emptyList());

        String refreshToken = jwtService.generateRefreshToken(userDetails);
        assertNotNull(refreshToken);

        String username = jwtService.extractUsername(refreshToken);
        assertEquals("admin", username);

        String tokenType = jwtService.extractClaim(refreshToken, claims -> claims.get("tokenType", String.class));
        assertEquals("refresh", tokenType);
    }
}
