package com.services.active.models.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.services.active.models.types.AuthProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Document("users")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class User {
    @Id
    private String id;

    private String googleId;

    private String email;
    private String username;

    private String firstName;
    private String lastName;

    @JsonIgnore
    private String passwordHash;
    @JsonIgnore
    private AuthProvider provider;

    private LocalDate createdAt;

    @Indexed
    private String timezone;

    private String activeRoutineId;

    private boolean registrationCompleted = false;

    @Builder.Default
    private StreakInfo streak = new StreakInfo();

    // New field to store push notification tokens (one user can have multiple devices)
    @Builder.Default
    private List<String> pushTokens = new ArrayList<>();

    @Builder.Default
    private NotificationPreferences notificationPreferences = new NotificationPreferences(false, new ArrayList<>());

    // Nested body measurements (optional). Null if not provided at signup.
    private BodyMeasurements measurements;

    public void setNotificationPreferences(Integer notificationPreferences) {
        if (notificationPreferences == null) {
            return; // ignore null frequency updates
        }
        setNotificationPreferences(notificationPreferences.intValue());
    }

    public void setNotificationPreferences(int notificationPreferences) {
        if (this.notificationPreferences == null) {
            this.notificationPreferences = new NotificationPreferences(false, new ArrayList<>());
        }
        if (notificationPreferences <= 0) {
            this.notificationPreferences.setEmailNotificationsEnabled(false);
            this.notificationPreferences.getSchedule().clear();
        } else {
            this.notificationPreferences.setEmailNotificationsEnabled(true);
            List<String> newSchedule = new ArrayList<>();
            java.time.LocalTime startTime = java.time.LocalTime.of(9, 0);  // 09:00
            java.time.LocalTime endTime = java.time.LocalTime.of(21, 0);   // 21:00
            if (notificationPreferences == 1) {
                newSchedule.add(startTime.toString());
            } else {
                long totalMinutes = java.time.temporal.ChronoUnit.MINUTES.between(startTime, endTime);
                long intervalMinutes = totalMinutes / (notificationPreferences - 1);
                for (int i = 0; i < notificationPreferences; i++) {
                    java.time.LocalTime nextTime = startTime.plusMinutes(intervalMinutes * i);
                    newSchedule.add(nextTime.toString());
                }
            }
            this.notificationPreferences.setSchedule(newSchedule);
        }
    }
}
