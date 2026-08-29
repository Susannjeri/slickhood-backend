package org.pms.silverocean.service.notification;

import org.pms.silverocean.service.notification.common.NotificationType;

public record NotificationDTO(String formattedMessage, String recipient, NotificationType notificationType) {
}
