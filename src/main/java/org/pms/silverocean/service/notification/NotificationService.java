package org.pms.silverocean.service.notification;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.database.pms.entities.Notification;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.notification.common.NotificationChannel;
import org.pms.silverocean.service.notification.common.NotificationDao;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.security.EncryptionService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final EncryptionService encryptionService;
    private final NotificationDao notificationDao;
    private final UserDao userDao;
    private final Map<String, NotificationSender> senders;
    private final ApplicationEventPublisher events;
    private Set<String> superAdminEmails;


    @PostConstruct
    public void init() {
        superAdminEmails = userDao.findActiveSuperAdminAccounts().stream().map(Users::getEmail).collect(Collectors.toSet());
    }

    @Async
    public void sendNotification(NotificationDTO notificationDTO) {
        queueNotification(notificationDTO);
    }

    public long queueNotification(NotificationDTO notificationDTO) {
        long notificationId = createNotification(notificationDTO.recipient(), notificationDTO.formattedMessage(), notificationDTO.notificationType());
        events.publishEvent(new NotificationQueued(notificationId, notificationDTO));
        return notificationId;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void deliverAfterCommit(NotificationQueued queued) {
        NotificationSender sender = getPlatform(queued.notification().notificationType().getChannel());
        sender.send(queued.notification(), queued.notificationId());
    }

    public void sendEmailToSuperAdmin(NotificationType notificationType, String formattedMessage) {
        if (NotificationChannel.EMAIL.equals(notificationType.getChannel())) {
            superAdminEmails.forEach(email -> sendNotification(new NotificationDTO(formattedMessage, email, notificationType)));
        }
    }

    private long createNotification(String recipient, String message, NotificationType notificationType) {
        Notification notification = new Notification();
        notification.setType(notificationType.name());
        notification.setRecipient(recipient);
        notification.setMessage(encryptionService.encrypt(message));
        notification.setChannel(notificationType.getChannel().name());
        notification.setRetry(notificationType.isRetry());
        notification.setRetries(0);
        notification.setUpdatedOn(LocalDateTime.now());
        notification.setActive(true);


        notificationDao.save(notification);
        return notification.getId();
    }

    private NotificationSender getPlatform(NotificationChannel channel) {
        NotificationSender sender = senders.get(channel.name());
        if (sender == null) {
            throw new IllegalArgumentException("Unsupported notification channel type: " + channel.name());
        }
        return sender;
    }

    public record NotificationQueued(long notificationId, NotificationDTO notification) {}
}
