package com.services.active.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.services.active.config.IntegrationTestBase;
import com.services.active.config.user.TestUserContext;
import com.services.active.config.user.WithTestUser;
import com.services.active.dto.AuthRequest;
import com.services.active.dto.UpdateUserRequest;
import com.services.active.models.user.User;
import com.services.active.repository.UserRepository;
import com.services.active.services.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithTestUser
@SpringBootTest
@AutoConfigureMockMvc
class UserUpdateControllerTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuthService authService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("PATCH /api/user/me -> 200 OK partial update only provided fields")
    void updateUser_partialProvided(@TestUserContext String token, @TestUserContext User user) throws Exception {
        String originalLast = user.getLastName();
        UpdateUserRequest req = UpdateUserRequest.builder()
                .firstName("UpdatedFirst")
                .timezone("Europe/London")
                .build();

        mockMvc.perform(patch("/api/user/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("UpdatedFirst"))
                .andExpect(jsonPath("$.timezone").value("Europe/London"));

        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertThat(refreshed.getFirstName()).isEqualTo("UpdatedFirst");
        assertThat(refreshed.getTimezone()).isEqualTo("Europe/London");
        assertThat(refreshed.getLastName()).isEqualTo(originalLast); // unchanged
    }

    @Test
    @DisplayName("PATCH /api/user/me -> 200 OK empty payload no changes")
    void updateUser_emptyPayloadNoOp(@TestUserContext String token, @TestUserContext User user) throws Exception {
        UpdateUserRequest empty = new UpdateUserRequest();
        mockMvc.perform(patch("/api/user/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(empty)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()));

        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertThat(refreshed.getFirstName()).isEqualTo(user.getFirstName());
        assertThat(refreshed.getLastName()).isEqualTo(user.getLastName());
        assertThat(refreshed.getEmail()).isEqualTo(user.getEmail());
    }

    @Test
    @DisplayName("PATCH /api/user/me -> 409 CONFLICT when updating to existing email")
    void updateUser_conflictEmail(@TestUserContext String token, @TestUserContext User user) throws Exception {
        // Create second user with different email
        AuthRequest second = new AuthRequest();
        second.setUsername("seconduser");
        second.setEmail("second@example.com");
        second.setFirstName("Second");
        second.setLastName("User");
        second.setPassword("StrongP@ssw0rd");
        authService.signup(second);

        UpdateUserRequest req = UpdateUserRequest.builder()
                .email("second@example.com") // attempt to take existing email
                .build();

        mockMvc.perform(patch("/api/user/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already exists"));

        // Ensure original user's email not changed
        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertThat(refreshed.getEmail()).isEqualTo(user.getEmail());
    }
}

