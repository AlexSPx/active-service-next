package com.services.active.services;

import com.niamedtech.expo.exposerversdk.ExpoPushNotificationClient;
import com.niamedtech.expo.exposerversdk.request.PushNotification;
import com.services.active.models.user.User;
import com.services.active.models.user.UserPushToken;
import com.services.active.repository.UserPushTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ExpoPushNotificationService {

    private ExpoPushNotificationClient expoClient;
    private final UserPushTokenRepository pushTokenRepository;

    public ExpoPushNotificationService(UserPushTokenRepository pushTokenRepository) {
        this.pushTokenRepository = pushTokenRepository;
        CloseableHttpClient httpClient = HttpClients.createDefault();
        this.expoClient = ExpoPushNotificationClient.builder()
                .setHttpClient(httpClient)
                .build();
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
            expoClient.sendPushNotifications(notifications);
            return notifications.size();
        } catch (IOException e) {
            log.error("Failed to bulk send streak reminders", e);
            return 0;
        }
    }
}
