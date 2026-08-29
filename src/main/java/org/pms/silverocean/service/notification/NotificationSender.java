package org.pms.silverocean.service.notification;

public interface NotificationSender {
    void send(NotificationDTO notificationDTO, long notificationId);
}
