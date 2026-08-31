package org.pms.silverocean.service.visitor.wrappers;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.pms.silverocean.service.visitor.enums.VisitorStatus;

public record UpdateVisitorStatusRequest(
        @NotNull(message = "Status is required")
        VisitorStatus status,
        @Size(max = 20, message = "Vehicle plate must not exceed 20 characters")
        String vehiclePlate
) {
}
