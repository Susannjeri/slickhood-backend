package org.pms.silverocean.controller.wrappers;

import org.pms.silverocean.service.notification.common.NotificationChannel;

public record ShareInviteDTO(long inviteId, String recipient, NotificationChannel notificationChannel) {
}
