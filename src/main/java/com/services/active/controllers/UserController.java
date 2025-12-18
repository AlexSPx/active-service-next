package com.services.active.controllers;

import com.services.active.dto.RegisterPushTokenRequest;
import com.services.active.dto.UpdateUserRequest;
import com.services.active.dto.GoogleAuthRequest;
import com.services.active.dto.TokenResponse;
import com.services.active.models.user.User;
import com.services.active.services.UserService;
import com.services.active.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService; // inject auth service for linking

    @GetMapping("/me")
    public User getCurrentUser(Principal principal) {
        return userService.getUserById(principal.getName());
    }

    @PatchMapping("/me")
    @Operation(summary = "Update current user partially", description = "Updates only provided fields; missing fields remain unchanged")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User updated successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "409", description = "Email already exists")
    })
    public User updateCurrentUser(Principal principal, @RequestBody UpdateUserRequest request) {
        return userService.updateUser(principal.getName(), request);
    }

    @PostMapping("/me/push-token")
    public User registerPushToken(Principal principal, @RequestBody RegisterPushTokenRequest request) {
        return userService.registerPushToken(principal.getName(), request.getToken());
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete current user and all related data")
    public void deleteCurrentUser(Principal principal) {
        userService.deleteUserAndData(principal.getName());
    }

    @PostMapping("/me/link-google")
    @Operation(summary = "Link Google account", description = "Switch authentication from LOCAL to GOOGLE by verifying a Google ID token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account linked successfully"),
            @ApiResponse(responseCode = "401", description = "Invalid ID token or unauthorized"),
            @ApiResponse(responseCode = "409", description = "Account already linked or Google account in use")
    })
    public TokenResponse linkGoogle(Principal principal, @RequestBody GoogleAuthRequest request) {
        return authService.linkGoogleAccount(principal.getName(), request.getIdToken());
    }
}
