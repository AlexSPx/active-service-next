package com.services.active.services;

import com.services.active.models.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.issuer:active-service}")
    private String issuer;

    @Value("${jwt.audience:active-client}")
    private String audience;

    @Value("${jwt.clockSkewSeconds:30}")
    private long clockSkewSeconds;

    private final long EXPIRATION_TIME = 1000L * 60 * 60 * 24; // 24h
    private final long REFRESH_EXPIRATION_TIME = 1000L * 60 * 60 * 24 * 7; // 7 days

    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret must be at least 256 bits (32 bytes) for HS256");
        }
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date exp = new Date(System.currentTimeMillis() + EXPIRATION_TIME);

        return Jwts.builder()
                .subject(user.getId())
                .issuer(issuer)
                .audience().add(audience).and()
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(exp)
                .signWith(key(), Jwts.SIG.HS256)
                .compact();
    }

    public String generateRefreshToken(User user) {
        Date now = new Date();
        Date exp = new Date(System.currentTimeMillis() + REFRESH_EXPIRATION_TIME);

        return Jwts.builder()
                .subject(user.getId())
                .issuer(issuer)
                .audience().add(audience).and()
                .id(UUID.randomUUID().toString())
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(exp)
                .signWith(key(), Jwts.SIG.HS256)
                .compact();
    }

    public Claims parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .clockSkewSeconds(clockSkewSeconds)
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (claims.getSubject() == null || claims.getSubject().isBlank()) {
                throw new JwtException("Missing subject");
            }
            if (issuer != null && !issuer.equals(claims.getIssuer())) {
                throw new JwtException("Invalid issuer");
            }
            if (audience != null) {
                var aud = claims.getAudience();
                if (aud == null || aud.stream().noneMatch(audience::equals)) {
                    throw new JwtException("Invalid audience");
                }
            }
            return claims;
        } catch (ExpiredJwtException e) {
            throw e; // propagate for filter to handle as 401
        } catch (JwtException e) {
            throw e; // invalid signature/claims
        } catch (Exception e) {
            throw new JwtException("Token parsing error", e);
        }
    }

    public Claims parseRefreshToken(String token) {
        Claims claims = parseToken(token);
        if (!"refresh".equals(claims.get("type"))) {
            throw new JwtException("Invalid token type");
        }
        return claims;
    }
}
