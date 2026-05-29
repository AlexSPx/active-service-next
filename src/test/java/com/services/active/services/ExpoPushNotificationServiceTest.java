package com.services.active.services;

import com.niamedtech.expo.exposerversdk.ExpoPushNotificationClient;
import com.niamedtech.expo.exposerversdk.response.ReceiptResponse;
import com.niamedtech.expo.exposerversdk.response.Status;
import com.niamedtech.expo.exposerversdk.response.TicketResponse;
import com.services.active.models.user.User;
import com.services.active.models.user.UserPushToken;
import com.services.active.repository.UserPushTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
        ExpoPushNotificationService service = new ExpoPushNotificationService(pushTokenRepository, "");
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
        ExpoPushNotificationService service = new ExpoPushNotificationService(pushTokenRepository, "");
        service.setExpoClient(expoClient);
        User u2 = userWithoutTokens();

        when(pushTokenRepository.findAllByUserId(u2.getId())).thenReturn(List.of());

        int sent = service.sendStreakReminder(List.of(u2));
        assertThat(sent).isZero();
        verify(expoClient, never()).sendPushNotifications(anyList());
    }

    @Test
    void mockSend_sendsToAllDistinctStoredTokens() throws Exception {
        ExpoPushNotificationService service = new ExpoPushNotificationService(pushTokenRepository, "");
        service.setExpoClient(expoClient);

        when(pushTokenRepository.findAll()).thenReturn(List.of(
                UserPushToken.builder().token("ExponentPushToken[token1]").build(),
                UserPushToken.builder().token("ExponentPushToken[token1]").build(),
                UserPushToken.builder().token("ExponentPushToken[token2]").build(),
                UserPushToken.builder().token(" ").build()
        ));
        TicketResponse.Ticket t1 = new TicketResponse.Ticket();
        t1.setStatus(Status.OK);
        t1.setId("ticket-1");
        TicketResponse.Ticket t2 = new TicketResponse.Ticket();
        t2.setStatus(Status.OK);
        t2.setId("ticket-2");
        when(expoClient.sendPushNotifications(anyList())).thenReturn(List.of(t1, t2));

        ExpoPushNotificationService.MockNotificationResult result = service.sendMockNotificationToAllTokens();

        assertThat(result.tokensTargeted()).isEqualTo(2);
        assertThat(result.accepted()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        assertThat(result.tickets()).extracting("ticketId").containsExactly("ticket-1", "ticket-2");
        verify(expoClient).sendPushNotifications(anyList());
    }

    @Test
    void mockReceipts_returnsReceiptStatuses() throws Exception {
        ExpoPushNotificationService service = new ExpoPushNotificationService(pushTokenRepository, "");
        service.setExpoClient(expoClient);

        ReceiptResponse.Receipt receipt = new ReceiptResponse.Receipt();
        receipt.setStatus(Status.OK);
        when(expoClient.getPushNotificationReceipts(List.of("ticket-1")))
                .thenReturn(Map.of("ticket-1", receipt));

        ExpoPushNotificationService.MockReceiptResult result =
                service.getMockNotificationReceipts(List.of("ticket-1"));

        assertThat(result.requested()).isEqualTo(1);
        assertThat(result.found()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.receipts()).extracting("status").containsExactly("OK");
    }
}
