package com.services.active.controllers;

import com.services.active.config.CookieHelper;
import com.services.active.dto.TokenRefreshRequest;
import com.services.active.dto.TokenResponse;
import com.services.active.services.AuthService;
import com.services.active.services.WorkosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication endpoints powered by WorkOS")
public class AuthController {

    private static final String PLATFORM_HEADER = "X-Platform";
    private static final String WEB_PLATFORM = "web";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private final AuthService authService;
    private final WorkosService workosService;
    private final CookieHelper cookieHelper;

    @PostMapping("/workos/callback")
    @Operation(
        summary = "Authenticate with WorkOS",
        description = "Exchange WorkOS authorization code for JWT tokens. For web platform (X-Platform: web), " +
                "tokens are set as HttpOnly cookies. For native platform, tokens are returned in the response body."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User authenticated successfully",
                content = @Content(schema = @Schema(implementation = TokenResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid authorization code")
    })
    public ResponseEntity<TokenResponse> workosCallback(
            @RequestParam("code") String code,
            @Parameter(description = "Platform type: 'web' for cookie-based auth, 'native' for token in body")
            @RequestHeader(value = PLATFORM_HEADER, required = false) String platform,
            HttpServletResponse response) {

        TokenResponse tokenResponse = authService.authenticateWithWorkos(code);

        if (isWebPlatform(platform)) {
            cookieHelper.setTokenCookies(response, tokenResponse.getToken(), tokenResponse.getRefreshToken());
            // Return empty body for web - tokens are in cookies
            return ResponseEntity.ok(new TokenResponse(null, null));
        }

        // Native platform - return tokens in body
        return ResponseEntity.ok(tokenResponse);
    }

    @PostMapping("/refresh")
    @Operation(
        summary = "Refresh JWT tokens",
        description = "Refresh access and refresh tokens. For web platform, reads refresh token from cookie " +
                "and sets new tokens as cookies. For native platform, reads from body and returns in body."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Tokens refreshed successfully",
                content = @Content(schema = @Schema(implementation = TokenResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    public ResponseEntity<TokenResponse> refreshTokens(
            @RequestBody(required = false) TokenRefreshRequest request,
            @Parameter(description = "Platform type: 'web' for cookie-based auth, 'native' for token in body")
            @RequestHeader(value = PLATFORM_HEADER, required = false) String platform,
            HttpServletRequest servletRequest,
            HttpServletResponse response) {

        String refreshToken;

        if (isWebPlatform(platform)) {
            // Web platform: get refresh token from cookie
            refreshToken = extractRefreshTokenFromCookie(servletRequest);
            if (refreshToken == null) {
                return ResponseEntity.status(401).build();
            }
        } else {
            // Native platform: get refresh token from request body
            if (request == null || request.getRefreshToken() == null) {
                return ResponseEntity.badRequest().build();
            }
            refreshToken = request.getRefreshToken();
        }

        TokenResponse tokenResponse = workosService.refreshTokens(refreshToken);

        if (isWebPlatform(platform)) {
            cookieHelper.setTokenCookies(response, tokenResponse.getToken(), tokenResponse.getRefreshToken());
            // Return empty body for web - tokens are in cookies
            return ResponseEntity.ok(new TokenResponse(null, null));
        }

        // Native platform - return tokens in body
        return ResponseEntity.ok(tokenResponse);
    }

    @PostMapping("/logout")
    @Operation(
        summary = "Logout user",
        description = "Clears authentication cookies for web platform. For native platform, no server-side action needed."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Logged out successfully")
    })
    public ResponseEntity<Void> logout(
            @Parameter(description = "Platform type: 'web' for cookie-based auth, 'native' for token in body")
            @RequestHeader(value = PLATFORM_HEADER, required = false) String platform,
            HttpServletResponse response) {

        if (isWebPlatform(platform)) {
            cookieHelper.clearTokenCookies(response);
        }

        return ResponseEntity.ok().build();
    }

    private boolean isWebPlatform(String platform) {
        return WEB_PLATFORM.equalsIgnoreCase(platform);
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            return Arrays.stream(cookies)
                    .filter(c -> REFRESH_TOKEN_COOKIE.equals(c.getName()))
                    .findFirst()
                    .map(Cookie::getValue)
                    .orElse(null);
        }
        return null;
    }
}
