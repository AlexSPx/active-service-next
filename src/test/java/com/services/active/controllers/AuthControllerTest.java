package com.services.active.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.services.active.config.IntegrationTestBase;
import com.services.active.dto.AuthRequest;
import com.services.active.services.AuthService;
import com.services.active.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class AuthControllerTest extends IntegrationTestBase {

    private final AuthService authService;
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserRepository userRepository;

    @Test
    @DisplayName("POST /api/auth/signup -> 200 OK returns token on success")
    void signup_success() throws Exception {
        Map<String, Object> body = Map.of(
                "username", "johnny",
                "email", "johnny@example.com",
                "firstName", "John",
                "lastName", "Doe",
                "password", "StrongP@ssw0rd"
        );

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/auth/signup -> 409 CONFLICT when email exists")
    void signup_conflict_emailExists() throws Exception {
        AuthRequest setup = new AuthRequest();
        setup.setUsername("johnny");
        setup.setEmail("existing@example.com");
        setup.setFirstName("John");
        setup.setLastName("Doe");
        setup.setPassword("StrongP@ssw0rd");
        authService.signup(setup);

        Map<String, Object> body = Map.of(
                "username", "johnny",
                "email", "existing@example.com",
                "firstName", "John",
                "lastName", "Doe",
                "password", "StrongP@ssw0rd"
        );

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already exists"));
    }

    @Test
    @DisplayName("POST /api/auth/login -> 200 OK returns token on valid credentials")
    void login_success() throws Exception {
        AuthRequest setup = new AuthRequest();
        setup.setUsername("jane");
        setup.setEmail("jane@example.com");
        setup.setFirstName("Jane");
        setup.setLastName("Doe");
        setup.setPassword("StrongerP@ss1");
        authService.signup(setup);

        Map<String, Object> login = Map.of(
                "email", "jane@example.com",
                "password", "StrongerP@ss1"
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/auth/login -> 401 UNAUTHORIZED when user not found")
    void login_userNotFound() throws Exception {
        Map<String, Object> login = Map.of(
                "email", "nouser@example.com",
                "password", "anything"
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    @DisplayName("POST /api/auth/login -> 401 UNAUTHORIZED when password invalid (user setup via service)")
    void login_invalidPassword() throws Exception {
        // Create user via service
        AuthRequest setup = new AuthRequest();
        setup.setUsername("jack");
        setup.setEmail("jack@example.com");
        setup.setFirstName("Jack");
        setup.setLastName("Frost");
        setup.setPassword("CorrectP@ss2");
        authService.signup(setup);

        Map<String, Object> badLogin = Map.of(
                "email", "jack@example.com",
                "password", "WrongPass"
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/signup -> sets provided timezone")
    void signup_withTimezone_setsTimezone() throws Exception {
        Map<String, Object> body = Map.of(
                "username", "tzjohnny",
                "email", "tzjohnny@example.com",
                "firstName", "Tz",
                "lastName", "Johnny",
                "password", "StrongP@ssw0rd",
                "timezone", "America/New_York"
        );

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        var saved = userRepository.findByEmail("tzjohnny@example.com").orElseThrow();
        assertEquals("America/New_York", saved.getTimezone());
        assertEquals(LocalDate.now(), saved.getCreatedAt());
    }

    @Test
    @DisplayName("POST /api/auth/signup -> defaults timezone to UTC when missing")
    void signup_withoutTimezone_defaultsUTC() throws Exception {
        Map<String, Object> body = Map.of(
                "username", "utcuser",
                "email", "utcuser@example.com",
                "firstName", "Utc",
                "lastName", "User",
                "password", "StrongP@ssw0rd"
        );

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        var saved = userRepository.findByEmail("utcuser@example.com").orElseThrow();
        assertEquals("UTC", saved.getTimezone());
        assertEquals(LocalDate.now(), saved.getCreatedAt());
    }

    @Test
    @DisplayName("POST /api/auth/signup -> blank timezone defaults to UTC")
    void signup_blankTimezone_defaultsUTC() throws Exception {
        Map<String, Object> body = Map.of(
                "username", "blanktz",
                "email", "blanktz@example.com",
                "firstName", "Blank",
                "lastName", "Tz",
                "password", "StrongP@ssw0rd",
                "timezone", "   "
        );

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        var saved = userRepository.findByEmail("blanktz@example.com").orElseThrow();
        assertEquals("UTC", saved.getTimezone());
        assertEquals(LocalDate.now(), saved.getCreatedAt());
    }
}
