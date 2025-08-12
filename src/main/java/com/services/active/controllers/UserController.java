package com.services.active.controllers;

import com.services.active.models.User;
import com.services.active.services.UserService;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.security.Principal;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public Mono<User> getCurrentUser(
            @Parameter(hidden = true) @AuthenticationPrincipal Mono<Principal> principalMono) {
        return principalMono
                .flatMap(principal -> userService.getUserById(principal.getName()))
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")));
    }
}
