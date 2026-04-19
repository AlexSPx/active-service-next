package com.services.active.controllers;

import com.services.active.dto.RegisterPushTokenRequest;
import com.services.active.dto.UpdateUserRequest;
import com.services.active.models.user.FullUser;
import com.services.active.models.user.User;
import com.services.active.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public FullUser getCurrentUser(Principal principal) {
        if (principal == null) {
            log.warn("Rejecting current user fetch request because principal is missing");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        log.info("Received current user fetch request");
        return userService.getUserById(principal.getName());
    }

    @PatchMapping("/me")
    @Operation(summary = "Update current user partially", description = "Updates only provided fields; missing fields remain unchanged")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User updated successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "409", description = "Email already exists")
    })
    public FullUser updateCurrentUser(Principal principal, @RequestBody UpdateUserRequest request) {
        if (principal == null) {
            log.warn("Rejecting current user update request because principal is missing");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        log.info("Received current user update request");
        return userService.updateUser(principal.getName(), request);
    }

    @PostMapping("/me/push-token")
    public User registerPushToken(Principal principal, @RequestBody RegisterPushTokenRequest request) {
        if (principal == null) {
            log.warn("Rejecting push token registration request because principal is missing");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        log.info("Received push token registration request");
        return userService.registerPushToken(principal.getName(), request.getToken());
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete current user and all related data")
    public void deleteCurrentUser(Principal principal) {
        if (principal == null) {
            log.warn("Rejecting current user deletion request because principal is missing");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        log.info("Received current user deletion request");
        userService.deleteUserAndData(principal.getName());
    }
}
