package com.services.active.services;

import com.niamedtech.expo.exposerversdk.ExpoPushNotificationClient;
import com.services.active.models.StreakInfo;
import com.services.active.models.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpoPushNotificationServiceTest {

    @Mock
    private ExpoPushNotificationClient expoClient;

    private User userWithTokens() {
        User u = new User();
        u.setId("u1");
        u.setPushTokens(new ArrayList<>(List.of("ExponentPushToken[token1]")));
        u.setStreak(StreakInfo.builder().currentStreak(3).build());
        return u;
    }

    private User userWithoutTokens() {
        User u = new User();
        u.setId("u2");
        u.setPushTokens(new ArrayList<>());
        return u;
    }

    @Test
    void bulkSend_sendsOnlyForUsersWithTokens() throws Exception {
        ExpoPushNotificationService service = new ExpoPushNotificationService();
        service.setExpoClient(expoClient);
        User u1 = userWithTokens();
        User u2 = userWithoutTokens();

        // Stub to avoid unexpected IOException when the client is invoked
        when(expoClient.sendPushNotifications(anyList())).thenReturn(null);

        int sent = service.sendStreakReminder(List.of(u1, u2));
        assertThat(sent).isEqualTo(1);
    }

    @Test
    void bulkSend_noUsersWithTokens_returnsZeroAndDoesNotCallClient() throws Exception {
        ExpoPushNotificationService service = new ExpoPushNotificationService();
        service.setExpoClient(expoClient);
        User u2 = userWithoutTokens();

        int sent = service.sendStreakReminder(List.of(u2));
        assertThat(sent).isZero();
        verify(expoClient, never()).sendPushNotifications(anyList());
    }
}
