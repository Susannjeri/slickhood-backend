package org.pms.silverocean.service.notification;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.pms.silverocean.database.pms.entities.Notification;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.notification.common.NotificationDao;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.security.EncryptionService;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;

class NotificationServiceTest {
    @Test
    void inAppNotificationIsStoredForExistingActiveUserWithoutExternalDelivery() {
        EncryptionService encryption = mock(EncryptionService.class);
        NotificationDao dao = mock(NotificationDao.class);
        UserDao users = mock(UserDao.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        Users recipient = Users.builder().email("member@example.com").build();
        recipient.setActive(true);
        when(users.findByEmail("member@example.com")).thenReturn(java.util.Optional.of(recipient));
        when(encryption.encrypt("Review invitation: https://app.slickhood.com/lease/onboard?token=safe"))
                .thenReturn(new byte[]{9, 1});
        NotificationService service = new NotificationService(encryption, dao, users, Map.of(), events);

        assertThat(service.queueInAppNotificationForExistingUser(
                " Member@Example.com ", "INVITE_RECEIVED",
                "Review invitation: https://app.slickhood.com/lease/onboard?token=safe")).isTrue();

        ArgumentCaptor<Notification> stored = ArgumentCaptor.forClass(Notification.class);
        verify(dao).save(stored.capture());
        assertThat(stored.getValue().getRecipient()).isEqualTo("member@example.com");
        assertThat(stored.getValue().getChannel()).isEqualTo("IN_APP");
        assertThat(stored.getValue().getType()).isEqualTo("INVITE_RECEIVED");
        assertThat(stored.getValue().isDelivered()).isTrue();
        assertThat(stored.getValue().isRetry()).isFalse();
        verifyNoInteractions(events);
    }

    @Test
    void inAppNotificationIsNotStoredForUnknownOrInactiveAccount() {
        EncryptionService encryption = mock(EncryptionService.class);
        NotificationDao dao = mock(NotificationDao.class);
        UserDao users = mock(UserDao.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        when(users.findByEmail("missing@example.com")).thenReturn(java.util.Optional.empty());
        NotificationService service = new NotificationService(encryption, dao, users, Map.of(), events);

        assertThat(service.queueInAppNotificationForExistingUser(
                "missing@example.com", "INVITE_RECEIVED", "Review invitation")).isFalse();

        verifyNoInteractions(dao, encryption, events);
    }

    @Test
    void queuePersistsBeforePublishingAndDeliveryStartsOnlyFromAfterCommitListener() {
        EncryptionService encryption = mock(EncryptionService.class);
        NotificationDao dao = mock(NotificationDao.class);
        UserDao users = mock(UserDao.class);
        NotificationSender email = mock(NotificationSender.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        when(encryption.encrypt("Balance due")).thenReturn(new byte[]{4, 2});
        when(dao.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification stored = invocation.getArgument(0);
            stored.setId(91L);
            return 91L;
        });
        NotificationService service = new NotificationService(encryption, dao, users, Map.of("EMAIL", email), events);
        NotificationDTO request = new NotificationDTO("Balance due", "owner@example.com",
                NotificationType.SERVICE_CHARGE_OVERDUE_EMAIL);

        assertThat(service.queueNotification(request)).isEqualTo(91L);

        verify(email, never()).send(any(), anyLong());
        ArgumentCaptor<NotificationService.NotificationQueued> queued =
                ArgumentCaptor.forClass(NotificationService.NotificationQueued.class);
        verify(events).publishEvent(queued.capture());
        assertThat(queued.getValue().notificationId()).isEqualTo(91L);

        service.deliverAfterCommit(queued.getValue());
        verify(email).send(request, 91L);
    }

    @Test
    void superAdminEscalationUsesCurrentActiveRecipientsWithoutRestart() {
        EncryptionService encryption = mock(EncryptionService.class);
        NotificationDao dao = mock(NotificationDao.class);
        UserDao users = mock(UserDao.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        Users first = Users.builder().email("first-admin@example.com").build();
        Users second = Users.builder().email("second-admin@example.com").build();
        when(users.findActiveSuperAdminAccounts()).thenReturn(Set.of(first), Set.of(second));
        when(encryption.encrypt(any())).thenReturn(new byte[]{1});
        when(dao.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification stored = invocation.getArgument(0);
            stored.setId(100L);
            return 100L;
        });
        NotificationService service = new NotificationService(encryption, dao, users, Map.of(), events);

        service.sendEmailToSuperAdmin(NotificationType.SERVICE_CHARGE_OVERDUE_EMAIL, "Escalation");
        service.sendEmailToSuperAdmin(NotificationType.SERVICE_CHARGE_OVERDUE_EMAIL, "Escalation");

        ArgumentCaptor<Notification> stored = ArgumentCaptor.forClass(Notification.class);
        verify(dao, times(2)).save(stored.capture());
        assertThat(stored.getAllValues()).extracting(Notification::getRecipient)
                .containsExactly("first-admin@example.com", "second-admin@example.com");
    }
}
