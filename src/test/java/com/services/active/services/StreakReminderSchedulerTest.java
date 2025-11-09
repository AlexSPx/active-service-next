package com.services.active.services;

import com.services.active.models.User;
import com.services.active.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
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
    }

    @ParameterizedTest
    @ValueSource(strings = {"2025-03-01T00:00:00Z", "2025-03-01T00:15:00Z", "2025-03-01T00:30:00Z", "2025-03-01T00:59:59Z"})
    void sendsReminder_whenTokyoIsAt9AM(String timeString) {
        Clock clock = Clock.fixed(Instant.parse(timeString), ZoneOffset.UTC);

        when(userRepository.findByTimezoneIn(anyList())).thenAnswer(invocation -> {
            List<String> zones = invocation.getArgument(0);
            return zones.contains("Asia/Tokyo") ? List.of(tokyoUser) : List.of();
        });
        when(pushService.sendStreakReminder(anyList())).thenReturn(1);

        StreakReminderScheduler scheduler = new StreakReminderScheduler(userRepository, pushService);
        scheduler.setClock(clock);
        scheduler.send9amLocalReminders();

        // Verify we looked up zones including Tokyo and bulk-sent that user
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> zonesCaptor = ArgumentCaptor.forClass(List.class);
        verify(userRepository, times(1)).findByTimezoneIn(zonesCaptor.capture());
        assertThat(zonesCaptor.getValue()).contains("Asia/Tokyo");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<User>> usersCaptor = ArgumentCaptor.forClass(List.class);
        verify(pushService, times(1)).sendStreakReminder(usersCaptor.capture());
        assertThat(usersCaptor.getValue()).containsExactly(tokyoUser);
        verifyNoMoreInteractions(pushService);
    }

    @Test
    void doesNotSend_whenTokyoIsNotAt9AM() {
        // 2025-03-01T01:00:00Z -> Tokyo local time 10:00
        Clock clock = Clock.fixed(Instant.parse("2025-03-01T01:00:00Z"), ZoneOffset.UTC);

        when(userRepository.findByTimezoneIn(anyList())).thenAnswer(invocation -> {
            List<String> zones = invocation.getArgument(0);
            return zones.contains("Asia/Tokyo") ? List.of(tokyoUser) : List.of();
        });

        StreakReminderScheduler scheduler = new StreakReminderScheduler(userRepository, pushService);
        scheduler.setClock(clock);
        scheduler.send9amLocalReminders();

        verify(userRepository, times(1)).findByTimezoneIn(anyList());
        verify(pushService, never()).sendStreakReminder(anyList());
        verifyNoMoreInteractions(pushService);
    }
}
