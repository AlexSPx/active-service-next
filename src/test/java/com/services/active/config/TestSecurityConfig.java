package com.services.active.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Test security configuration that uses a mock JwtDecoder for testing.
 * This allows tests to generate and validate JWTs without calling the WorkOS JWK endpoint.
 */
@Configuration
@EnableWebSecurity
@Profile("test")
public class TestSecurityConfig {

    @Bean
    @Primary
    public JwtDecoder testJwtDecoder() {
        return token -> {
            try {
                System.out.println("========================================");
                System.out.println("DEBUG: Mock JWT Decoder called");
                System.out.println("DEBUG: Raw token: " + token);

                // Parse the JWT token (format: header.payload.signature)
                String[] parts = token.split("\\.");
                if (parts.length != 3) {
                    throw new IllegalArgumentException("Invalid JWT format. Expected 3 parts, got " + parts.length);
                }

                // Decode the payload (second part)
                String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                System.out.println("DEBUG: Decoded payload: " + payload);

                // Parse the payload JSON to extract claims
                Map<String, Object> claims = parseJsonToMap(payload);
                System.out.println("DEBUG: Extracted sub claim: " + claims.get("sub"));

                // Create headers
                Map<String, Object> headers = new HashMap<>();
                headers.put("alg", "RS256");
                headers.put("typ", "JWT");

                // Ensure sub claim exists
                if (!claims.containsKey("sub") || claims.get("sub") == null || claims.get("sub").toString().isEmpty()) {
                    claims.put("sub", "test_user_default");
                }

                System.out.println("========================================");

                Instant now = Instant.now();
                Instant exp = claims.containsKey("exp") && claims.get("exp") instanceof Number
                        ? Instant.ofEpochSecond(((Number) claims.get("exp")).longValue())
                        : now.plusSeconds(3600);

                return new Jwt(
                        token,
                        now,
                        exp,
                        headers,
                        claims
                );
            } catch (Exception e) {
                System.err.println("ERROR in mock decoder: " + e.getMessage());
                e.printStackTrace();

                // Fallback: create a minimal valid JWT with default subject
                Map<String, Object> headers = new HashMap<>();
                headers.put("alg", "RS256");
                headers.put("typ", "JWT");

                Map<String, Object> claims = new HashMap<>();
                claims.put("sub", "test_user_fallback");

                Instant now = Instant.now();
                return new Jwt(
                        token,
                        now,
                        now.plusSeconds(3600),
                        headers,
                        claims
                );
            }
        };
    }

    /**
     * Simple JSON parser for extracting claims from JWT payload
     * Handles basic JSON objects with string and number values
     */
    private Map<String, Object> parseJsonToMap(String json) {
        Map<String, Object> map = new HashMap<>();

        // Remove curly braces and whitespace
        json = json.trim().replaceAll("^\\{|\\}$", "");

        // Split by comma (simple parser, assumes no nested objects)
        String[] pairs = json.split(",");

        for (String pair : pairs) {
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length == 2) {
                String key = keyValue[0].trim().replaceAll("\"", "");
                String value = keyValue[1].trim().replaceAll("\"", "");

                // Try to parse as number, otherwise keep as string
                try {
                    if (value.contains(".")) {
                        map.put(key, Double.parseDouble(value));
                    } else {
                        map.put(key, Long.parseLong(value));
                    }
                } catch (NumberFormatException e) {
                    map.put(key, value);
                }
            }
        }

        return map;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/legal/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/webjars/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> jwt.decoder(testJwtDecoder()))
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                response.setStatus(HttpStatus.UNAUTHORIZED.value()))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                response.setStatus(HttpStatus.FORBIDDEN.value()))
                );
        return http.build();
    }
}