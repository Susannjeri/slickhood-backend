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

    @Test void unverifiedPhoneCannotSelectPhoneBoundNotifications(){
        Users user=new Users();user.setEmail("owner@example.test");user.setPhoneNumber("+254700000001");user.setPhoneVerified(false);
        when(users.getUserObject()).thenReturn(user);when(notifications.countUnreadForRecipients(any())).thenReturn(0L);
        new NotificationReportService(notifications,sms,users,encryption,org.mockito.Mockito.mock(NotificationActionResolver.class)).getMyUnreadNotificationCount();
        @SuppressWarnings("unchecked") var recipients=(ArgumentCaptor<Collection<String>>)(ArgumentCaptor<?>)ArgumentCaptor.forClass(Collection.class);
        verify(notifications).countUnreadForRecipients(recipients.capture());assertThat(recipients.getValue()).containsExactly("owner@example.test");
    }

    @Test
    void personalFeedUsesOnlyTheAuthenticatedUsersRecipientVariants() {
        Users user = new Users();
        user.setEmail("Owner@Example.com");
        user.setPhoneNumber("+254700000001");user.setPhoneVerified(true);
        Notification notification = new Notification();
        notification.setId(7L);
        notification.setRecipient("+254700000001");
        notification.setChannel("SMS");
        notification.setType("PAYMENT_REMINDER");
        notification.setMessage(new byte[]{1});
        when(users.getUserObject()).thenReturn(user);
        when(notifications.getNotificationsForRecipients(any(), any())).thenReturn(new PageImpl<>(java.util.List.of(notification)));
        when(encryption.decrypt(notification.getMessage())).thenReturn(new DecryptDTO(false, "Your service charge is due"));

        var result = new NotificationReportService(notifications, sms, users, encryption, org.mockito.Mockito.mock(NotificationActionResolver.class))
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
        notification.setType("PAYMENT_RECEIPT_EMAIL");
        notification.setMessage(new byte[]{2});
        when(users.getUserObject()).thenReturn(user);
        when(notifications.findById(8L)).thenReturn(java.util.Optional.of(notification));
        when(notifications.markRecipientRead(org.mockito.ArgumentMatchers.eq(8L), any())).thenReturn(true);
        when(encryption.decrypt(notification.getMessage())).thenReturn(new DecryptDTO(false, "Read me"));

        var result = new NotificationReportService(notifications, sms, users, encryption, org.mockito.Mockito.mock(NotificationActionResolver.class))
                .markMyNotificationRead(8L);

        assertThat(result.read()).isTrue();
        verify(notifications).markRecipientRead(org.mockito.ArgumentMatchers.eq(8L), any());
        verify(notifications, org.mockito.Mockito.never()).saveEntity(any());
    }

    @Test
    void unreadCountUsesOnlyTheAuthenticatedUsersRecipientVariants() {
        Users user = new Users();
        user.setEmail("Owner@Example.com");
        user.setPhoneNumber("+254700000001");user.setPhoneVerified(true);
        when(users.getUserObject()).thenReturn(user);
        when(notifications.countUnreadForRecipients(any())).thenReturn(4L);

        long count = new NotificationReportService(notifications, sms, users, encryption, org.mockito.Mockito.mock(NotificationActionResolver.class))
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

        var service = new NotificationReportService(notifications, sms, users, encryption, org.mockito.Mockito.mock(NotificationActionResolver.class));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.markMyNotificationRead(9L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void securityChallengesCannotBeReadOrDecryptedThroughTheInbox() {
        Users user = new Users(); user.setEmail("buyer@example.test");
        when(users.getUserObject()).thenReturn(user);
        var service = new NotificationReportService(notifications, sms, users, encryption, org.mockito.Mockito.mock(NotificationActionResolver.class));
        for (String type : java.util.List.of("EMAIL_OTP", "OTP_SMS", "SOKO_DELIVERY_RECOVERY_EMAIL", "SOKO_DELIVERY_CODE_EMAIL", "NEW_LOGIN_OTP")) {
            Notification secret = new Notification(); secret.setId(20L); secret.setActive(true); secret.setRecipient(user.getEmail()); secret.setType(type); secret.setMessage(new byte[]{4});
            when(notifications.findById(20L)).thenReturn(java.util.Optional.of(secret));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.markMyNotificationRead(20L)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        }
        org.mockito.Mockito.verifyNoInteractions(encryption);
        verify(notifications, org.mockito.Mockito.never()).markRecipientRead(org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    void anUnreadableMessageDoesNotTakeDownTheFeed() {
        Users user = new Users(); user.setEmail("buyer@example.test"); when(users.getUserObject()).thenReturn(user);
        Notification corrupt = new Notification(); corrupt.setId(30L); corrupt.setType("PAYMENT_RECEIPT_EMAIL"); corrupt.setMessage(new byte[]{8});
        when(notifications.getNotificationsForRecipients(any(), any())).thenReturn(new PageImpl<>(java.util.List.of(corrupt)));
        when(encryption.decrypt(corrupt.getMessage())).thenThrow(new IllegalStateException("Invalid ciphertext"));
        var result = new NotificationReportService(notifications, sms, users, encryption, org.mockito.Mockito.mock(NotificationActionResolver.class)).getMyNotifications(PageRequest.of(0,20));
        assertThat(result.getContent().getFirst().message()).isEqualTo("Message unavailable. Contact support if you need help.");
    }
}
