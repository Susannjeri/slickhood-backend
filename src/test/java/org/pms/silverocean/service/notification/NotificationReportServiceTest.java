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
        });
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> recipients = ArgumentCaptor.forClass(Collection.class);
        org.mockito.Mockito.verify(notifications).getNotificationsForRecipients(any(), recipients.capture());
        assertThat(recipients.getValue()).containsExactlyInAnyOrder(
                "Owner@Example.com", "owner@example.com", "+254700000001", "254700000001");
    }
}
