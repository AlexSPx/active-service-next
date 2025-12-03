package com.services.active.controllers;

import com.services.active.dto.RegisterPushTokenRequest;
import com.services.active.dto.UpdateUserRequest;
import com.services.active.models.user.User;
import com.services.active.services.UserService;
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
}
