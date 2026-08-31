package org.pms.silverocean.service.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.entities.Notification;
import org.pms.silverocean.service.notification.common.NotificationDao;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.security.DecryptDTO;
import org.pms.silverocean.service.security.EncryptionService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRetryRoutineTest {
    @Mock NotificationDao notifications;
    @Mock EncryptionService encryption;
    @Mock NotificationSender email;

    @Test
    void restartRecoveryRequeuesPersistedUndeliveredNotification() {
        Notification stored = stored(NotificationType.SERVICE_CHARGE_OVERDUE_EMAIL);
        when(email.retryDelaySeconds()).thenReturn(300);
        when(email.maxRetries()).thenReturn(10);
        when(notifications.findRetryCandidates(eq("EMAIL"), any(LocalDateTime.class), eq(10), eq(100))).thenReturn(List.of(42L));
        when(notifications.findById(42L)).thenReturn(Optional.of(stored));
        when(encryption.decrypt(stored.getMessage())).thenReturn(new DecryptDTO(false, "Outstanding balance"));

        new NotificationRetryRoutine(notifications, encryption, Map.of("EMAIL", email)).recoverRetries();

        verify(email).retry(new NotificationDTO("Outstanding balance", "owner@example.com",
                NotificationType.SERVICE_CHARGE_OVERDUE_EMAIL), 42L);
        verify(notifications, never()).stopRetry(42L);
    }

    @Test
    void corruptPersistedMetadataIsRemovedFromTheHotRetryLoop() {
        Notification stored = stored(NotificationType.SERVICE_CHARGE_OVERDUE_EMAIL);
        stored.setType("REMOVED_NOTIFICATION_TYPE");
        when(email.retryDelaySeconds()).thenReturn(300);
        when(email.maxRetries()).thenReturn(10);
        when(notifications.findRetryCandidates(eq("EMAIL"), any(LocalDateTime.class), eq(10), eq(100))).thenReturn(List.of(42L));
        when(notifications.findById(42L)).thenReturn(Optional.of(stored));

        new NotificationRetryRoutine(notifications, encryption, Map.of("EMAIL", email)).recoverRetries();

        verify(notifications).stopRetry(42L);
        verify(email, never()).retry(any(), eq(42L));
    }

    private Notification stored(NotificationType type) {
        Notification notification = new Notification();
        notification.setId(42L);
        notification.setType(type.name());
        notification.setChannel("EMAIL");
        notification.setRecipient("owner@example.com");
        notification.setMessage(new byte[]{1, 2, 3});
        notification.setRetry(true);
        notification.setActive(true);
        return notification;
    }
}
