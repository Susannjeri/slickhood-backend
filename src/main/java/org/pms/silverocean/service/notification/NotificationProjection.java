package org.pms.silverocean.service.notification;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

public interface NotificationProjection {
    long getNotificationId();
    String getChannel();
    String getNotificationType();

    String getRecipient();

    ZonedDateTime getCreatedOn();

    boolean isRetry();
    boolean isDelivered();
    int getRetryCount();

    String getStatus();
    String getDescription();
    String getNetwork();
    double getCost();

    String getCurrency();
    String getCallbackIP();
    LocalDateTime getLastUpdateOn();
}
