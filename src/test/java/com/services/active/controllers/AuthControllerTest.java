package com.services.active.controllers;

import com.services.active.config.IntegrationTestBase;
import com.services.active.dto.AuthRequest;
import com.services.active.services.AuthService;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class AuthControllerTest extends IntegrationTestBase {

    private final AuthService authService;

    private final WebTestClient webTestClient;

    @Test
    @DisplayName("POST /api/auth/signup -> 200 OK returns token on success")
    void signup_success() {
        Map<String, Object> body = Map.of(
                "username", "johnny",
                "email", "johnny@example.com",
                "firstName", "John",
                "lastName", "Doe",
                "password", "StrongP@ssw0rd"
        );

        webTestClient.post()
                .uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.token").isNotEmpty();
    }

    @Test
    @DisplayName("POST /api/auth/signup -> 409 CONFLICT when email exists")
    void signup_conflict_emailExists() {
        AuthRequest setup = new AuthRequest();
        setup.setUsername("johnny");
        setup.setEmail("existing@example.com");
        setup.setFirstName("John");
        setup.setLastName("Doe");
        setup.setPassword("StrongP@ssw0rd");
        authService.signup(setup).block();

        Map<String, Object> body = Map.of(
                "username", "johnny",
                "email", "existing@example.com",
                "firstName", "John",
                "lastName", "Doe",
                "password", "StrongP@ssw0rd"
        );

        webTestClient.post()
                .uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.message").isEqualTo("Email already exists");
    }

    @Test
    @DisplayName("POST /api/auth/login -> 200 OK returns token on valid credentials")
    void login_success() {
        AuthRequest setup = new AuthRequest();
        setup.setUsername("jane");
        setup.setEmail("jane@example.com");
        setup.setFirstName("Jane");
        setup.setLastName("Doe");
        setup.setPassword("StrongerP@ss1");
        authService.signup(setup).block();

        Map<String, Object> login = Map.of(
                "email", "jane@example.com",
                "password", "StrongerP@ss1"
        );

        webTestClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(login)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.token").isNotEmpty();
    }

    @Test
    @DisplayName("POST /api/auth/login -> 401 UNAUTHORIZED when user not found")
    void login_userNotFound() {
        Map<String, Object> login = Map.of(
                "email", "nouser@example.com",
                "password", "anything"
        );

        webTestClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(login)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.message").isEqualTo("User not found");
    }

    @Test
    @DisplayName("POST /api/auth/login -> 401 UNAUTHORIZED when password invalid (user setup via service)")
    void login_invalidPassword() {
        // Create user via service
        AuthRequest setup = new AuthRequest();
        setup.setUsername("jack");
        setup.setEmail("jack@example.com");
        setup.setFirstName("Jack");
        setup.setLastName("Frost");
        setup.setPassword("CorrectP@ss2");
        authService.signup(setup).block();

        Map<String, Object> badLogin = Map.of(
                "email", "jack@example.com",
                "password", "WrongPass"
        );

        webTestClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(badLogin)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody();
    }
}
