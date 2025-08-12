package com.services.active.services;

import com.services.active.models.User;
import io.jsonwebtoken.Claims;
import lombok.AllArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = "jwt.secret=really_long_secret_that_is_at_least_256_bits_long")
@AllArgsConstructor(onConstructor_ = @Autowired)
class JwtServiceTest {

    private JwtService jwtService;
    private static User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId("testUserId");
    }

    @Test
    void testGenerateToken() {
        String token = jwtService.generateToken(testUser);

        assertNotNull(token, "Token should not be null");
        assertTrue(token.length() > 0, "Token should not be empty");
        assertTrue(token.split("\\.").length == 3, "Token should have 3 parts separated by dots");
    }

    @Test
    void testParseToken() {
        String token = jwtService.generateToken(testUser);

        Claims claims = jwtService.parseToken(token);

        assertNotNull(claims, "Claims should not be null");
        assertEquals(testUser.getId(), claims.getSubject(), "Subject should match user ID");
        assertNotNull(claims.getIssuedAt(), "Issued at should not be null");
        assertNotNull(claims.getExpiration(), "Expiration should not be null");
    }

    @Test
    void testTokenExpiration() {
        String token = jwtService.generateToken(testUser);
        Claims claims = jwtService.parseToken(token);

        long issuedAt = claims.getIssuedAt().getTime();
        long expiration = claims.getExpiration().getTime();
        long expectedDuration = 1000 * 60 * 60 * 24; // 24 hours

        assertEquals(expectedDuration, expiration - issuedAt, "Token should expire in 24 hours");
    }

    @Test
    void testParseInvalidToken() {
        String invalidToken = "invalid.token.here";

        assertThrows(Exception.class, () -> {
            jwtService.parseToken(invalidToken);
        }, "Parsing invalid token should throw an exception");
    }
}