package com.services.active.services;

import com.services.active.models.user.User;
import com.services.active.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StreakReminderSchedulerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ExpoPushNotificationService pushService;

    private User tokyoUser;

    @BeforeEach
    void setUp() {
        tokyoUser = new User();
        tokyoUser.setTimezone("Asia/Tokyo");
        tokyoUser.setPushTokens(new ArrayList<>(List.of("ExponentPushToken[abc]")));
        tokyoUser.setNotificationPreferences(1); // frequency 1 => [09:00]
    }

    @Test
    void sendsReminder_whenTokyoIsAt9AM() {
        Clock clock = Clock.fixed(Instant.parse("2025-03-01T00:00:00Z"), ZoneOffset.UTC); // 09:00 Tokyo
        when(userRepository.findUsersToNotify(anyString(), anyString())).thenReturn(List.of());
        when(userRepository.findUsersToNotify("Asia/Tokyo", "09:00")).thenReturn(List.of(tokyoUser));
        when(pushService.sendStreakReminder(anyList())).thenReturn(1);
        StreakReminderScheduler scheduler = new StreakReminderScheduler(userRepository, pushService);
        scheduler.setClock(clock);
        scheduler.processHourlyReminders();
        verify(pushService, times(1)).sendStreakReminder(argThat(list -> list.contains(tokyoUser) && list.size() == 1));
        verifyNoMoreInteractions(pushService);
    }

    @Test
    void doesNotSend_whenTokyoIsNotAtScheduledHour() {
        Clock clock = Clock.fixed(Instant.parse("2025-03-01T01:00:00Z"), ZoneOffset.UTC); // 10:00 Tokyo
        when(userRepository.findUsersToNotify(anyString(), anyString())).thenReturn(List.of());
        StreakReminderScheduler scheduler = new StreakReminderScheduler(userRepository, pushService);
        scheduler.setClock(clock);
        scheduler.processHourlyReminders();
        verify(pushService, never()).sendStreakReminder(anyList());
    }

    @Test
    void doesNotSend_whenNotificationsDisabled() {
        tokyoUser.setNotificationPreferences(0); // disabled
        Clock clock = Clock.fixed(Instant.parse("2025-03-01T00:00:00Z"), ZoneOffset.UTC); // 09:00 Tokyo
        when(userRepository.findUsersToNotify(anyString(), anyString())).thenReturn(List.of());
        StreakReminderScheduler scheduler = new StreakReminderScheduler(userRepository, pushService);
        scheduler.setClock(clock);
        scheduler.processHourlyReminders();
        verify(pushService, never()).sendStreakReminder(anyList());
    }

    @Test
    void multiFrequency_userOnlyTriggeredAtScheduledHour() {
        User multi = new User();
        multi.setTimezone("Asia/Tokyo");
        multi.setPushTokens(List.of("ExponentPushToken[multi]"));
        multi.setNotificationPreferences(3); // [09:00, 15:00, 21:00]
        Clock clockScheduled = Clock.fixed(Instant.parse("2025-03-01T06:00:00Z"), ZoneOffset.UTC); // 15:00 Tokyo
        when(userRepository.findUsersToNotify(anyString(), anyString())).thenReturn(List.of());
        when(userRepository.findUsersToNotify("Asia/Tokyo", "15:00")).thenReturn(List.of(multi));
        when(pushService.sendStreakReminder(anyList())).thenReturn(1);
        StreakReminderScheduler scheduler = new StreakReminderScheduler(userRepository, pushService);
        scheduler.setClock(clockScheduled);
        scheduler.processHourlyReminders();
        verify(pushService, times(1)).sendStreakReminder(argThat(list -> list.contains(multi)));
        // Next hour (16:00 Tokyo) should not send
        Clock clockNotScheduled = Clock.fixed(Instant.parse("2025-03-01T07:00:00Z"), ZoneOffset.UTC);
        scheduler.setClock(clockNotScheduled);
        scheduler.processHourlyReminders();
        verifyNoMoreInteractions(pushService);
    }
}
