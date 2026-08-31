package org.pms.silverocean.service.visitor.wrappers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.pms.silverocean.service.visitor.enums.AccessDirection;

public record SmartGateDecisionRequest(
        @NotBlank @Size(min = 43, max = 43) String accessCode,
        @NotNull AccessDirection direction,
        @Size(max = 20) String vehiclePlate,
        @NotBlank @Size(max = 64) String correlationId
) {}
