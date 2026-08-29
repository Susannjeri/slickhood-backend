package org.pms.silverocean.service.notification.sms.africastalking.wrappers;

import java.time.LocalDateTime;

public record ATSMSDTO(long id, long notificationId, String status, String description, String network, double cost,
                       String currency, String callbackIP, LocalDateTime createdOn,
                       LocalDateTime lastUpdateOn) {
}
