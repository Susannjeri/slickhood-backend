package org.pms.silverocean.service.notification;

public interface NotificationSender {
    void send(NotificationDTO notificationDTO, long notificationId);
    void retry(NotificationDTO notificationDTO, long notificationId);
    int retryDelaySeconds();
    int maxRetries();
}
