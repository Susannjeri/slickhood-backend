package org.pms.silverocean.controller.wrappers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.pms.silverocean.service.notification.common.NotificationChannel;

public record ShareInviteDTO(@Positive long inviteId, @NotBlank String recipient, @NotNull NotificationChannel notificationChannel) {
}
