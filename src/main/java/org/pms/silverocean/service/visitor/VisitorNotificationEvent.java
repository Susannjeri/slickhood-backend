package org.pms.silverocean.service.visitor;

import org.pms.silverocean.service.notification.common.NotificationChannel;
import org.pms.silverocean.service.notification.common.NotificationType;

public enum VisitorNotificationEvent {
    VISITOR_BOOKING_CONFIRMATION(
            NotificationType.VISITOR_BOOKING_CONFIRMATION_SMS,
            NotificationType.VISITOR_BOOKING_CONFIRMATION_EMAIL
    ),
    VISITOR_ARRIVAL(
            NotificationType.VISITOR_ARRIVAL_SMS,
            NotificationType.VISITOR_ARRIVAL_EMAIL
    ),
    VISITOR_DEPARTURE(
            NotificationType.VISITOR_DEPARTURE_SMS,
            NotificationType.VISITOR_DEPARTURE_EMAIL
    );

    private final NotificationType smsType;
    private final NotificationType emailType;

    VisitorNotificationEvent(NotificationType smsType, NotificationType emailType) {
        this.smsType = smsType;
        this.emailType = emailType;
    }

    public NotificationType typeFor(NotificationChannel channel) {
        return switch (channel) {
            case SMS -> smsType;
            case EMAIL -> emailType;
        };
    }
}
