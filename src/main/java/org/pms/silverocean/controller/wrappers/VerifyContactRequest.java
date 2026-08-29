package org.pms.silverocean.controller.wrappers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.pms.silverocean.service.notification.common.NotificationChannel;

public record VerifyContactRequest(
        @NotBlank String contact,
        @NotNull NotificationChannel channel
) { }
