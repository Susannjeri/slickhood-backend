package org.pms.silverocean.service.notification;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

public record MyNotificationDTO(long id, String channel, String notificationType, String message,
                                boolean delivered, ZonedDateTime createdOn, LocalDateTime lastUpdatedOn) {
}
