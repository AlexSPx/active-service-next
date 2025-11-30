package com.services.active.controllers;

import com.services.active.dto.AuthRequest;
import com.services.active.dto.LoginRequest;
import com.services.active.dto.TokenResponse;
import com.services.active.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication and registration endpoints")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/signup")
    @Operation(
        summary = "Register a new user",
        description = "Creates a new user account and returns a JWT token for authentication"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User registered successfully",
                content = @Content(schema = @Schema(implementation = TokenResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "409", description = "Email already exists")
    })
    public ResponseEntity<TokenResponse> signup(@RequestBody @NonNull AuthRequest request) {
        return ResponseEntity.ok(authService.signup(request));
    }

    @PostMapping("/login")
    @Operation(
        summary = "Authenticate user",
        description = "Authenticate user with email and password, returns JWT token on success"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User authenticated successfully",
                content = @Content(schema = @Schema(implementation = TokenResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public ResponseEntity<TokenResponse> login(@RequestBody @NonNull LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/google")
    @Operation(
        summary = "Authenticate with Google",
        description = "Authenticate user with Google ID token, returns JWT token on success"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User authenticated successfully",
                content = @Content(schema = @Schema(implementation = TokenResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid ID token")
    })
    public ResponseEntity<TokenResponse> googleLogin(@RequestBody @NonNull com.services.active.dto.GoogleAuthRequest request) {
        return ResponseEntity.ok(authService.loginWithGoogle(request.getIdToken()));
    }

    @PostMapping("/refresh")
    @Operation(
        summary = "Refresh access token",
        description = "Obtain a new access token using a valid refresh token"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token refreshed successfully",
                content = @Content(schema = @Schema(implementation = TokenResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    public ResponseEntity<TokenResponse> refresh(@RequestBody @NonNull com.services.active.dto.RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request.getRefreshToken()));
    }
}
