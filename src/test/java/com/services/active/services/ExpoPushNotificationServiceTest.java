package com.services.active.services;

import com.niamedtech.expo.exposerversdk.ExpoPushNotificationClient;
import com.services.active.models.user.User;
import com.services.active.models.user.UserPushToken;
import com.services.active.repository.UserPushTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpoPushNotificationServiceTest {

    @Mock
    private ExpoPushNotificationClient expoClient;

    @Mock
    private UserPushTokenRepository pushTokenRepository;

    private User userWithTokens() {
        UUID userId = UUID.randomUUID();
        User u = new User();
        u.setId(userId);
        u.setCurrentStreak(3);
        u.setNextWorkoutDeadline(LocalDate.now().plusDays(1));
        return u;
    }

    private User userWithoutTokens() {
        UUID userId = UUID.randomUUID();
        User u = new User();
        u.setId(userId);
        return u;
    }

    @Test
    void bulkSend_sendsOnlyForUsersWithTokens() throws Exception {
        ExpoPushNotificationService service = new ExpoPushNotificationService(pushTokenRepository);
        service.setExpoClient(expoClient);
        User u1 = userWithTokens();
        User u2 = userWithoutTokens();

        when(pushTokenRepository.findAllByUserId(u1.getId()))
                .thenReturn(List.of(UserPushToken.builder().userId(u1.getId()).token("ExponentPushToken[token1]").build()));
        when(pushTokenRepository.findAllByUserId(u2.getId()))
                .thenReturn(List.of());
        when(expoClient.sendPushNotifications(anyList())).thenReturn(null);

        int sent = service.sendStreakReminder(List.of(u1, u2));
        assertThat(sent).isEqualTo(1);
    }

    @Test
    void bulkSend_noUsersWithTokens_returnsZeroAndDoesNotCallClient() throws Exception {
        ExpoPushNotificationService service = new ExpoPushNotificationService(pushTokenRepository);
        service.setExpoClient(expoClient);
        User u2 = userWithoutTokens();

        when(pushTokenRepository.findAllByUserId(u2.getId())).thenReturn(List.of());

        int sent = service.sendStreakReminder(List.of(u2));
        assertThat(sent).isZero();
        verify(expoClient, never()).sendPushNotifications(anyList());
    }
}
