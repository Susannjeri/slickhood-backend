package org.pms.silverocean.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.service.notification.common.NotificationDao;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.security.EncryptionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationRetryRoutine {
    private static final int BATCH_SIZE = 100;
    private final NotificationDao notifications;
    private final EncryptionService encryption;
    private final Map<String, NotificationSender> senders;

    @Scheduled(fixedDelayString = "${pms.notification.recovery.poll-delay-ms:60000}")
    public void recoverRetries() {
        LocalDateTime now = LocalDateTime.now();
        senders.forEach((channel, sender) -> notifications.findRetryCandidates(channel,
                        now.minusSeconds(sender.retryDelaySeconds()), sender.maxRetries(), BATCH_SIZE)
                .forEach(id -> retry(id, sender)));
    }

    private void retry(long id, NotificationSender sender) {
        try {
            var notification = notifications.findById(id).orElse(null);
            if (notification == null || notification.isDelivered() || !notification.isRetry()) return;
            NotificationType type;
            try {
                type = NotificationType.valueOf(notification.getType());
            } catch (IllegalArgumentException | NullPointerException invalidType) {
                notifications.stopRetry(id);
                log.error("Notification {} has an unsupported stored type", id);
                return;
            }
            var decrypted = encryption.decrypt(notification.getMessage());
            if (decrypted == null) {
                notifications.stopRetry(id);
                log.error("Notification {} cannot be retried because its stored delivery metadata is invalid", id);
                return;
            }
            sender.retry(new NotificationDTO(decrypted.decryptedValue(), notification.getRecipient(), type), id);
        } catch (Exception failure) {
            // A database outage, executor rejection or key-service outage is not corrupt payload.
            // Keep the persisted retry eligible; never log provider bodies or decrypted content.
            log.warn("Notification {} recovery deferred ({})", id, failure.getClass().getSimpleName());
        }
    }
}
