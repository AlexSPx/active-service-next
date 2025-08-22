package com.services.active.services;

import com.services.active.models.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    // Test config values
    private String secret = "really_long_secret_that_is_at_least_256_bits_long";
    private String issuer = "active-service";
    private String audience = "active-client";
    private long clockSkewSeconds = 30;

    private static User testUser;

    @BeforeEach
    void setUp() throws Exception {
        testUser = new User();
        testUser.setId("testUserId");

        jwtService = new JwtService();
        setPrivateField(jwtService, "secret", secret);
        setPrivateField(jwtService, "issuer", issuer);
        setPrivateField(jwtService, "audience", audience);
        setPrivateField(jwtService, "clockSkewSeconds", clockSkewSeconds);
        // call validateSecret()
        var method = JwtService.class.getDeclaredMethod("validateSecret");
        method.setAccessible(true);
        method.invoke(jwtService);
    }

    @Test
    void testGenerateToken() {
        String token = jwtService.generateToken(testUser);

        assertNotNull(token, "Token should not be null");
        assertTrue(token.length() > 0, "Token should not be empty");
        assertEquals(3, token.split("\\.").length, "Token should have 3 parts separated by dots");
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
        long expectedDuration = 1000L * 60 * 60 * 24; // 24 hours

        assertEquals(expectedDuration, expiration - issuedAt, "Token should expire in 24 hours");
    }

    @Test
    void testParseInvalidToken() {
        String invalidToken = "invalid.token.here";

        assertThrows(Exception.class, () -> jwtService.parseToken(invalidToken),
                "Parsing invalid token should throw an exception");
    }

    @Test
    void testClaimsContainIssuerAndAudience() {
        String token = jwtService.generateToken(testUser);
        Claims claims = jwtService.parseToken(token);

        assertEquals(issuer, claims.getIssuer(), "Issuer should match");
        Set<String> aud = claims.getAudience();
        assertNotNull(aud, "Audience set should not be null");
        assertTrue(aud.contains(audience), "Audience should contain expected value");
    }

    @Test
    void testInvalidAudienceShouldFailValidation() throws Exception {
        // Generate a valid token
        String token = jwtService.generateToken(testUser);
        // Change expected audience to force validation failure
        setPrivateField(jwtService, "audience", "unexpected-audience");

        assertThrows(Exception.class, () -> jwtService.parseToken(token), "Invalid audience must fail");
        // restore expected audience
        setPrivateField(jwtService, "audience", audience);
    }

    @Test
    void testInvalidIssuerShouldFailValidation() throws Exception {
        String token = jwtService.generateToken(testUser);
        // Force issuer mismatch
        setPrivateField(jwtService, "issuer", "unexpected-issuer");
        assertThrows(Exception.class, () -> jwtService.parseToken(token), "Invalid issuer must fail");
        setPrivateField(jwtService, "issuer", issuer);
    }

    @Test
    void testExpiredTokenIsRejected() {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        // Expired 2 minutes ago
        Date exp = new Date(System.currentTimeMillis() - 120_000);

        String token = Jwts.builder()
                .subject(testUser.getId())
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(now)
                .expiration(exp)
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThrows(ExpiredJwtException.class, () -> jwtService.parseToken(token),
                "Expired token should throw ExpiredJwtException");
    }

    @Test
    void testRecentlyExpiredWithinClockSkewIsAccepted() {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        // Expired 5 seconds ago; default skew is 30s
        Date exp = new Date(System.currentTimeMillis() - 5_000);

        String token = Jwts.builder()
                .subject(testUser.getId())
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(new Date(System.currentTimeMillis() - 60_000))
                .expiration(exp)
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        Claims claims = jwtService.parseToken(token);
        assertEquals(testUser.getId(), claims.getSubject());
    }

    // Helper to set private fields on the service for validation tests
    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}