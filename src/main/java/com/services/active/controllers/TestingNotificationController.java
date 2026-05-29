package com.services.active.controllers;

import com.services.active.services.ExpoPushNotificationService;
import com.services.active.services.ExpoPushNotificationService.MockReceiptResult;
import com.services.active.services.ExpoPushNotificationService.MockNotificationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/testing/notifications")
@RequiredArgsConstructor
@Profile("!prod")
public class TestingNotificationController {

    private final ExpoPushNotificationService pushNotificationService;

    @PostMapping("/mock")
    public MockNotificationResult sendMockNotificationToAllUsers() {
        return pushNotificationService.sendMockNotificationToAllTokens();
    }

    @PostMapping("/receipts")
    public MockReceiptResult getReceipts(@RequestBody ReceiptRequest request) {
        List<String> ids = request != null ? request.ids() : List.of();
        return pushNotificationService.getMockNotificationReceipts(ids);
    }

    public record ReceiptRequest(List<String> ids) {
    }
}
