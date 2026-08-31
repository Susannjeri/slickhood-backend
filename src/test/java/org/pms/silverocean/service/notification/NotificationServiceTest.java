package org.pms.silverocean.service.notification;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.pms.silverocean.database.pms.entities.Notification;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.notification.common.NotificationDao;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.security.EncryptionService;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {
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
}
