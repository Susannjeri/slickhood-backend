package org.pms.silverocean.service.visitor.wrappers;

import jakarta.validation.constraints.NotNull;
import org.pms.silverocean.service.visitor.enums.VisitorStatus;

public record UpdateVisitorStatusRequest(
        @NotNull(message = "Status is required")
        VisitorStatus status
) {
}
