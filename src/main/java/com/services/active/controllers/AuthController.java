package com.services.active.controllers;

import com.services.active.dto.TokenRefreshRequest;
import com.services.active.dto.TokenResponse;
import com.services.active.services.AuthService;
import com.services.active.services.WorkosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication endpoints powered by WorkOS")
public class AuthController {
    private final AuthService authService;
    private final WorkosService workosService;

    @PostMapping("/workos/callback")
    @Operation(
        summary = "Authenticate with WorkOS",
        description = "Exchange WorkOS authorization code for JWT tokens"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User authenticated successfully",
                content = @Content(schema = @Schema(implementation = TokenResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid authorization code")
    })
    public ResponseEntity<TokenResponse> workosCallback(@RequestParam("code") String code) {
        return ResponseEntity.ok(authService.authenticateWithWorkos(code));
    }

    @PostMapping("/refresh")
    @Operation(
        summary = "Refresh JWT tokens",
        description = "Refresh access and refresh tokens using a valid refresh token"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Tokens refreshed successfully",
                content = @Content(schema = @Schema(implementation = TokenResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    public ResponseEntity<TokenResponse> refreshTokens(@RequestBody @NonNull TokenRefreshRequest request) {
        return ResponseEntity.ok(workosService.refreshTokens(request.getRefreshToken()));
    }
}
