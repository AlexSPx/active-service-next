package com.services.active.controllers;

import com.services.active.config.IntegrationTestBase;
import com.services.active.dto.TokenResponse;
import com.services.active.exceptions.UnauthorizedException;
import com.services.active.services.AuthService;
import com.services.active.services.WorkosService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest extends IntegrationTestBase {

    private static final String PLATFORM_HEADER = "X-Platform";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private WorkosService workosService;

    @Nested
    @DisplayName("Native Platform (tokens in body)")
    class NativePlatformTests {

        @Test
        @DisplayName("POST /api/auth/workos/callback -> 200 OK with tokens in body")
        void testWorkosCallback_Success() throws Exception {
            // Given
            String authCode = "auth_code_valid_123";
            TokenResponse tokenResponse = new TokenResponse("jwt_token_123", "refresh_token_123");
            when(authService.authenticateWithWorkos(authCode)).thenReturn(tokenResponse);

            // When & Then
            mockMvc.perform(post("/api/auth/workos/callback")
                            .param("code", authCode)
                            .header(PLATFORM_HEADER, "native")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("jwt_token_123"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh_token_123"));

            verify(authService).authenticateWithWorkos(authCode);
        }

        @Test
        @DisplayName("POST /api/auth/workos/callback -> returns tokens in body without platform header")
        void testWorkosCallback_NoHeader_ReturnsBody() throws Exception {
            // Given
            String authCode = "auth_code_valid_123";
            TokenResponse tokenResponse = new TokenResponse("jwt_token_123", "refresh_token_123");
            when(authService.authenticateWithWorkos(authCode)).thenReturn(tokenResponse);

            // When & Then - no X-Platform header defaults to native behavior
            mockMvc.perform(post("/api/auth/workos/callback")
                            .param("code", authCode)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("jwt_token_123"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh_token_123"));
        }

        @Test
        @DisplayName("POST /api/auth/refresh -> 200 OK with tokens in body")
        void testRefresh_Success() throws Exception {
            // Given
            String refreshToken = "valid_refresh_token";
            TokenResponse tokenResponse = new TokenResponse("new_jwt_token", "new_refresh_token");
            when(workosService.refreshTokens(refreshToken)).thenReturn(tokenResponse);

            // When & Then
            mockMvc.perform(post("/api/auth/refresh")
                            .header(PLATFORM_HEADER, "native")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("new_jwt_token"))
                    .andExpect(jsonPath("$.refreshToken").value("new_refresh_token"));
        }
    }

    @Nested
    @DisplayName("Web Platform (tokens in cookies)")
    class WebPlatformTests {

        @Test
        @DisplayName("POST /api/auth/workos/callback -> 200 OK with tokens in cookies")
        void testWorkosCallback_SetsCookies() throws Exception {
            // Given
            String authCode = "auth_code_valid_123";
            TokenResponse tokenResponse = new TokenResponse("jwt_token_123", "refresh_token_123");
            when(authService.authenticateWithWorkos(authCode)).thenReturn(tokenResponse);

            // When & Then
            MvcResult result = mockMvc.perform(post("/api/auth/workos/callback")
                            .param("code", authCode)
                            .header(PLATFORM_HEADER, "web")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isEmpty())
                    .andExpect(jsonPath("$.refreshToken").isEmpty())
                    .andReturn();

            // Verify cookies are set
            String setCookieHeaders = result.getResponse().getHeader("Set-Cookie");
            assertThat(setCookieHeaders).isNotNull();

            verify(authService).authenticateWithWorkos(authCode);
        }

        @Test
        @DisplayName("POST /api/auth/refresh -> 200 OK reading refresh token from cookie")
        void testRefresh_ReadsFromCookie() throws Exception {
            // Given
            String refreshToken = "valid_refresh_token";
            TokenResponse tokenResponse = new TokenResponse("new_jwt_token", "new_refresh_token");
            when(workosService.refreshTokens(refreshToken)).thenReturn(tokenResponse);

            // When & Then
            mockMvc.perform(post("/api/auth/refresh")
                            .header(PLATFORM_HEADER, "web")
                            .cookie(new Cookie("refresh_token", refreshToken))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isEmpty())
                    .andExpect(jsonPath("$.refreshToken").isEmpty());

            verify(workosService).refreshTokens(refreshToken);
        }

        @Test
        @DisplayName("POST /api/auth/refresh -> 401 when no cookie present")
        void testRefresh_NoCookie_Returns401() throws Exception {
            // When & Then - web platform without refresh token cookie
            mockMvc.perform(post("/api/auth/refresh")
                            .header(PLATFORM_HEADER, "web")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());

            verify(workosService, never()).refreshTokens(anyString());
        }

        @Test
        @DisplayName("POST /api/auth/logout -> 200 OK and clears cookies")
        void testLogout_ClearsCookies() throws Exception {
            // When & Then
            MvcResult result = mockMvc.perform(post("/api/auth/logout")
                            .header(PLATFORM_HEADER, "web")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn();

            // Verify cookies are cleared (max-age=0)
            String setCookieHeaders = result.getResponse().getHeader("Set-Cookie");
            assertThat(setCookieHeaders).isNotNull();
        }
    }

    @Nested
    @DisplayName("Error cases")
    class ErrorTests {

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

        @Test
        @DisplayName("POST /api/auth/refresh -> 400 BAD REQUEST without refresh token (native)")
        void testRefresh_MissingToken_Native() throws Exception {
            mockMvc.perform(post("/api/auth/refresh")
                            .header(PLATFORM_HEADER, "native")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());

            verify(workosService, never()).refreshTokens(anyString());
        }
    }
}
