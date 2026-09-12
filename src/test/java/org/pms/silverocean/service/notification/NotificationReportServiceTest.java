package org.pms.silverocean.service.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.entities.Notification;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.notification.common.NotificationDao;
import org.pms.silverocean.service.notification.sms.SMSDao;
import org.pms.silverocean.service.security.DecryptDTO;
import org.pms.silverocean.service.security.EncryptionService;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationReportServiceTest {
    @Mock NotificationDao notifications;
    @Mock SMSDao sms;
    @Mock UserDao users;
    @Mock EncryptionService encryption;

    @Test
    void personalFeedUsesOnlyTheAuthenticatedUsersRecipientVariants() {
        Users user = new Users();
        user.setEmail("Owner@Example.com");
        user.setPhoneNumber("+254700000001");
        Notification notification = new Notification();
        notification.setId(7L);
        notification.setRecipient("+254700000001");
        notification.setChannel("SMS");
        notification.setType("PAYMENT_REMINDER");
        notification.setMessage(new byte[]{1});
        when(users.getUserObject()).thenReturn(user);
        when(notifications.getNotificationsForRecipients(any(), any())).thenReturn(new PageImpl<>(java.util.List.of(notification)));
        when(encryption.decrypt(notification.getMessage())).thenReturn(new DecryptDTO(false, "Your service charge is due"));

        var result = new NotificationReportService(notifications, sms, users, encryption)
                .getMyNotifications(PageRequest.of(0, 20));

        assertThat(result.getContent()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(7L);
            assertThat(item.message()).isEqualTo("Your service charge is due");
            assertThat(item.read()).isFalse();
        });
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> recipients = ArgumentCaptor.forClass(Collection.class);
        org.mockito.Mockito.verify(notifications).getNotificationsForRecipients(any(), recipients.capture());
        assertThat(recipients.getValue()).containsExactlyInAnyOrder(
                "Owner@Example.com", "owner@example.com", "+254700000001", "254700000001");
    }

    @Test
    void userCanMarkOnlyTheirOwnNotificationRead() {
        Users user = new Users();
        user.setEmail("owner@example.com");
        Notification notification = new Notification();
        notification.setId(8L);
        notification.setActive(true);
        notification.setRecipient("owner@example.com");
        notification.setMessage(new byte[]{2});
        when(users.getUserObject()).thenReturn(user);
        when(notifications.findById(8L)).thenReturn(java.util.Optional.of(notification));
        when(notifications.saveEntity(notification)).thenReturn(notification);
        when(encryption.decrypt(notification.getMessage())).thenReturn(new DecryptDTO(false, "Read me"));

        var result = new NotificationReportService(notifications, sms, users, encryption)
                .markMyNotificationRead(8L);

        assertThat(result.read()).isTrue();
        assertThat(notification.getViewedOn()).isNotNull();
        verify(notifications).saveEntity(notification);
    }

    @Test
    void unreadCountUsesOnlyTheAuthenticatedUsersRecipientVariants() {
        Users user = new Users();
        user.setEmail("Owner@Example.com");
        user.setPhoneNumber("+254700000001");
        when(users.getUserObject()).thenReturn(user);
        when(notifications.countUnreadForRecipients(any())).thenReturn(4L);

        long count = new NotificationReportService(notifications, sms, users, encryption)
                .getMyUnreadNotificationCount();

        assertThat(count).isEqualTo(4L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> recipients = ArgumentCaptor.forClass(Collection.class);
        verify(notifications).countUnreadForRecipients(recipients.capture());
        assertThat(recipients.getValue()).containsExactlyInAnyOrder(
                "Owner@Example.com", "owner@example.com", "+254700000001", "254700000001");
    }

    @Test
    void userCannotMarkAnotherRecipientsNotificationRead() {
        Users user = new Users();
        user.setEmail("owner@example.com");
        Notification notification = new Notification();
        notification.setId(9L);
        notification.setActive(true);
        notification.setRecipient("another@example.com");
        when(users.getUserObject()).thenReturn(user);
        when(notifications.findById(9L)).thenReturn(java.util.Optional.of(notification));

        var service = new NotificationReportService(notifications, sms, users, encryption);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.markMyNotificationRead(9L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
}
