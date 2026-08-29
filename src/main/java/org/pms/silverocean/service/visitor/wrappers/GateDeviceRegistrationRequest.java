package org.pms.silverocean.service.visitor.wrappers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record GateDeviceRegistrationRequest(
        @Positive long propertyId,
        @NotBlank @Size(max = 120) String displayName,
        @Size(max = 120) String gateName,
        @Size(max = 120) String laneName,
        @NotBlank @Size(max = 800) String ed25519PublicKey
) {}
