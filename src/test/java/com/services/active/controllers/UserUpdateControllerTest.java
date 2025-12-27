package com.services.active.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.services.active.config.IntegrationTestBase;
import com.services.active.config.user.TestUserContext;
import com.services.active.config.user.WithTestUser;
import com.services.active.dto.UpdateUserRequest;
import com.services.active.models.user.User;
import com.services.active.repository.UserRepository;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("PATCH /api/user/me -> 200 OK partial update only provided fields")
    void updateUser_partialProvided(@TestUserContext String token, @TestUserContext User user) throws Exception {
        UpdateUserRequest req = UpdateUserRequest.builder()
                .timezone("Europe/London")
                .build();

        mockMvc.perform(patch("/api/user/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Europe/London"));

        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertThat(refreshed.getTimezone()).isEqualTo("Europe/London");
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
        assertThat(refreshed.getTimezone()).isEqualTo(user.getTimezone());
    }

    @Test
    @DisplayName("PATCH /api/user/me -> 200 OK update firstName and lastName")
    void updateUser_updateNames(@TestUserContext String token, @TestUserContext User user) throws Exception {
        UpdateUserRequest req = UpdateUserRequest.builder()
                .firstName("NewFirst")
                .lastName("NewLast")
                .build();

        mockMvc.perform(patch("/api/user/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("NewFirst"))
                .andExpect(jsonPath("$.lastName").value("NewLast"));
    }
}
