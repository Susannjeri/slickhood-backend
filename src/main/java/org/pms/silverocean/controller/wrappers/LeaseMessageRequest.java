package org.pms.silverocean.controller.wrappers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record LeaseMessageRequest(@NotBlank @Size(max = 4000) String message, @Positive long leaseId) {
}
