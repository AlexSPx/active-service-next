package com.services.active.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.services.active.config.IntegrationTestBase;
import com.services.active.config.user.WithTestUser;
import com.services.active.config.user.TestUserContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithTestUser
@SpringBootTest
@AutoConfigureMockMvc
class UserPushTokenControllerTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("POST /api/user/me/push-token -> 200 OK adds token to user")
    void registerPushToken_success(@TestUserContext String token) throws Exception {
        Map<String, Object> body = Map.of("token", "ExponentPushToken[abcd1234]");

        mockMvc.perform(post("/api/user/me/push-token")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pushTokens[0]").value("ExponentPushToken[abcd1234]"))
                .andExpect(jsonPath("$.pushTokens.length()" ).value(1));
    }

    @Test
    @DisplayName("POST /api/user/me/push-token -> idempotent when token already exists")
    void registerPushToken_duplicateIdempotent(@TestUserContext String token) throws Exception {
        Map<String, Object> body = Map.of("token", "ExponentPushToken[dupToken]");

        // First registration
        mockMvc.perform(post("/api/user/me/push-token")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pushTokens.length()" ).value(1));

        // Second registration (same token) should not duplicate
        mockMvc.perform(post("/api/user/me/push-token")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pushTokens.length()" ).value(1))
                .andExpect(jsonPath("$.pushTokens[0]").value("ExponentPushToken[dupToken]"));
    }

    @Test
    @DisplayName("POST /api/user/me/push-token -> 400 BAD REQUEST when token blank")
    void registerPushToken_blank(@TestUserContext String token) throws Exception {
        Map<String, Object> body = Map.of("token", "   ");

        mockMvc.perform(post("/api/user/me/push-token")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Token is required"));
    }
}

