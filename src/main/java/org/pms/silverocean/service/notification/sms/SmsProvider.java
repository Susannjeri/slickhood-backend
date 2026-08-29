package org.pms.silverocean.service.notification.sms;

import org.pms.silverocean.service.notification.NotificationDTO;

public interface SmsProvider {
    int executeSend(NotificationDTO dto, long notificationId) throws Exception;

    boolean isRetryable(int statusCode);

    boolean supports(String providerName);
}
