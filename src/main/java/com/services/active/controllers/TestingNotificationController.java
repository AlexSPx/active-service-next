package com.services.active.controllers;

import com.services.active.services.ExpoPushNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/testing/notifications")
@RequiredArgsConstructor
@Profile("!prod")
public class TestingNotificationController {

    private final ExpoPushNotificationService pushNotificationService;

    @PostMapping("/mock")
    public Map<String, Integer> sendMockNotificationToAllUsers() {
        int tokensTargeted = pushNotificationService.sendMockNotificationToAllTokens();
        return Map.of("tokensTargeted", tokensTargeted);
    }
}
