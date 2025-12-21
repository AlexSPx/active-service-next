package com.services.active.controllers;

import com.services.active.config.IntegrationTestBase;
import com.services.active.dto.TokenResponse;
import com.services.active.exceptions.UnauthorizedException;
import com.services.active.services.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("POST /api/auth/workos/callback -> 200 OK with valid code")
    void testWorkosCallback_Success() throws Exception {
        // Given
        String authCode = "auth_code_valid_123";
        TokenResponse tokenResponse = new TokenResponse("jwt_token_123", "refresh_token_123");
        when(authService.authenticateWithWorkos(authCode)).thenReturn(tokenResponse);

        // When & Then
        mockMvc.perform(post("/api/auth/workos/callback")
                        .param("code", authCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt_token_123"))
                .andExpect(jsonPath("$.refreshToken").value("refresh_token_123"));

        verify(authService).authenticateWithWorkos(authCode);
    }

    @Test
    @DisplayName("POST /api/auth/workos/callback -> 401 UNAUTHORIZED with invalid code")
    void testWorkosCallback_InvalidCode() throws Exception {
        // Given
        String invalidCode = "invalid_code";
        when(authService.authenticateWithWorkos(invalidCode))
                .thenThrow(new UnauthorizedException("Invalid authentication code"));

        // When & Then
        mockMvc.perform(post("/api/auth/workos/callback")
                        .param("code", invalidCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verify(authService).authenticateWithWorkos(invalidCode);
    }

    @Test
    @DisplayName("POST /api/auth/workos/callback -> 400 BAD REQUEST without code parameter")
    void testWorkosCallback_MissingCode() throws Exception {
        mockMvc.perform(post("/api/auth/workos/callback")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(authService, never()).authenticateWithWorkos(anyString());
    }
}

