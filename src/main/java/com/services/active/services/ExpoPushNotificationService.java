package com.services.active.services;

import com.niamedtech.expo.exposerversdk.ExpoPushNotificationClient;
import com.niamedtech.expo.exposerversdk.request.PushNotification;
import com.niamedtech.expo.exposerversdk.response.ReceiptResponse;
import com.niamedtech.expo.exposerversdk.response.TicketResponse;
import com.services.active.models.user.User;
import com.services.active.models.user.UserPushToken;
import com.services.active.repository.UserPushTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ExpoPushNotificationService {

    private ExpoPushNotificationClient expoClient;
    private final UserPushTokenRepository pushTokenRepository;

    public ExpoPushNotificationService(UserPushTokenRepository pushTokenRepository) {
        this(pushTokenRepository, "");
    }

    @Autowired
    public ExpoPushNotificationService(
            UserPushTokenRepository pushTokenRepository,
            @Value("${expo.push.access-token:}") String expoAccessToken
    ) {
        this.pushTokenRepository = pushTokenRepository;
        CloseableHttpClient httpClient = HttpClients.createDefault();
        ExpoPushNotificationClient.Builder builder = ExpoPushNotificationClient.builder()
                .setHttpClient(httpClient);
        if (expoAccessToken != null && !expoAccessToken.trim().isEmpty()) {
            builder.setAccessToken(expoAccessToken);
            log.debug("Push Notifications token added");
        }
        this.expoClient = builder.build();
    }

    // Package-private for tests
    void setExpoClient(ExpoPushNotificationClient expoClient) {
        this.expoClient = expoClient;
    }

    public int sendStreakReminder(List<User> users) {
        if (users == null || users.isEmpty()) return 0;
        List<PushNotification> notifications = new ArrayList<>();
        for (User user : users) {
            List<String> tokens = pushTokenRepository.findAllByUserId(user.getId())
                    .stream().map(UserPushToken::getToken).toList();
            if (tokens.isEmpty()) continue;
            String title = "Keep your streak going";
            String body;
            if (user.getCurrentStreak() > 0) {
                var deadline = user.getNextWorkoutDeadline();
                String deadlineStr = deadline != null ? deadline.format(DateTimeFormatter.ISO_DATE) : "today";
                body = "You're on a " + user.getCurrentStreak() + " day streak. Next workout deadline: " + deadlineStr + ".";
            } else {
                body = "Start your streak today with your next workout!";
            }
            PushNotification notification = new PushNotification();
            notification.setTo(new ArrayList<>(tokens));
            notification.setTitle(title);
            notification.setChannelId("streak-reminders");
            notification.setBody(body);
            notifications.add(notification);
        }
        if (notifications.isEmpty()) return 0;
        try {
            List<TicketResponse.Ticket> tickets = expoClient.sendPushNotifications(notifications);
            logTicketFailures("streak reminder", tickets);
            return notifications.size();
        } catch (IOException e) {
            log.error("Failed to bulk send streak reminders", e);
            return 0;
        }
    }

    public MockNotificationResult sendMockNotificationToAllTokens() {
        List<String> tokens = pushTokenRepository.findAll().stream()
                .map(UserPushToken::getToken)
                .filter(token -> token != null && !token.trim().isEmpty())
                .distinct()
                .toList();

        if (tokens.isEmpty()) {
            return new MockNotificationResult(0, 0, 0, List.of());
        }

        List<PushNotification> notifications = tokens.stream()
                .map(this::mockNotificationForToken)
                .toList();

        try {
            List<TicketResponse.Ticket> tickets = expoClient.sendPushNotifications(notifications);
            List<MockNotificationTicket> results = new ArrayList<>();
            int accepted = 0;
            int failed = 0;

            for (int i = 0; i < tokens.size(); i++) {
                TicketResponse.Ticket ticket = tickets != null && i < tickets.size() ? tickets.get(i) : null;
                String status = ticket != null && ticket.getStatus() != null ? ticket.getStatus().name() : "MISSING";
                String message = ticket != null ? ticket.getMessage() : null;
                String error = null;
                if (ticket != null && ticket.getDetails() != null && ticket.getDetails().getError() != null) {
                    error = ticket.getDetails().getError().name();
                }
                if ("OK".equals(status)) {
                    accepted++;
                } else {
                    failed++;
                }
                String ticketId = ticket != null ? ticket.getId() : null;
                results.add(new MockNotificationTicket(maskToken(tokens.get(i)), ticketId, status, message, error));
            }

            if (failed > 0) {
                log.warn("Mock notification returned {} failed Expo tickets out of {}", failed, tokens.size());
            }

            return new MockNotificationResult(tokens.size(), accepted, failed, results);
        } catch (IOException e) {
            log.error("Failed to send mock notification to all tokens", e);
            return new MockNotificationResult(tokens.size(), 0, tokens.size(), List.of(
                    new MockNotificationTicket(null, null, "REQUEST_FAILED", e.getMessage(), e.getClass().getSimpleName())
            ));
        }
    }

    public MockReceiptResult getMockNotificationReceipts(List<String> ticketIds) {
        List<String> ids = ticketIds == null ? List.of() : ticketIds.stream()
                .filter(id -> id != null && !id.trim().isEmpty())
                .distinct()
                .toList();

        if (ids.isEmpty()) {
            return new MockReceiptResult(0, 0, 0, 0, List.of());
        }

        try {
            Map<String, ReceiptResponse.Receipt> receipts = expoClient.getPushNotificationReceipts(ids);
            List<MockNotificationReceipt> results = new ArrayList<>();
            int failed = 0;
            int missing = 0;

            for (String id : ids) {
                ReceiptResponse.Receipt receipt = receipts != null ? receipts.get(id) : null;
                if (receipt == null) {
                    missing++;
                    results.add(new MockNotificationReceipt(id, "MISSING", null, null));
                    continue;
                }

                String status = receipt.getStatus() != null ? receipt.getStatus().name() : "MISSING";
                String message = receipt.getMessage();
                String error = null;
                if (receipt.getDetails() != null && receipt.getDetails().getError() != null) {
                    error = receipt.getDetails().getError().name();
                }
                if (!"OK".equals(status)) {
                    failed++;
                }
                results.add(new MockNotificationReceipt(id, status, message, error));
            }

            return new MockReceiptResult(ids.size(), ids.size() - missing, missing, failed, results);
        } catch (IOException e) {
            log.error("Failed to get Expo push receipts", e);
            return new MockReceiptResult(ids.size(), 0, ids.size(), ids.size(), List.of(
                    new MockNotificationReceipt(null, "REQUEST_FAILED", e.getMessage(), e.getClass().getSimpleName())
            ));
        }
    }

    private PushNotification mockNotificationForToken(String token) {
        PushNotification notification = new PushNotification();
        notification.setTo(List.of(token));
        notification.setTitle("Active test notification");
        notification.setChannelId("streak-reminders");
        notification.setBody("This is a test push notification from Active.");
        return notification;
    }

    private String maskToken(String token) {
        int suffixStart = Math.max(0, token.length() - 6);
        return "***" + token.substring(suffixStart);
    }

    private void logTicketFailures(String context, List<TicketResponse.Ticket> tickets) {
        if (tickets == null || tickets.isEmpty()) return;

        long failed = tickets.stream()
                .filter(ticket -> ticket == null || ticket.getStatus() == null || !"OK".equals(ticket.getStatus().name()))
                .count();
        if (failed == 0) return;

        log.warn("Expo {} returned {} failed ticket(s) out of {}", context, failed, tickets.size());
        tickets.stream()
                .filter(ticket -> ticket == null || ticket.getStatus() == null || !"OK".equals(ticket.getStatus().name()))
                .limit(5)
                .forEach(ticket -> {
                    String message = ticket != null ? ticket.getMessage() : null;
                    String error = null;
                    if (ticket != null && ticket.getDetails() != null && ticket.getDetails().getError() != null) {
                        error = ticket.getDetails().getError().name();
                    }
                    log.warn("Expo {} ticket failure: error={}, message={}", context, error, message);
                });
    }

    public record MockNotificationResult(
            int tokensTargeted,
            int accepted,
            int failed,
            List<MockNotificationTicket> tickets
    ) {
    }

    public record MockNotificationTicket(
            String token,
            String ticketId,
            String status,
            String message,
            String error
    ) {
    }

    public record MockReceiptResult(
            int requested,
            int found,
            int missing,
            int failed,
            List<MockNotificationReceipt> receipts
    ) {
    }

    public record MockNotificationReceipt(
            String ticketId,
            String status,
            String message,
            String error
    ) {
    }
}
