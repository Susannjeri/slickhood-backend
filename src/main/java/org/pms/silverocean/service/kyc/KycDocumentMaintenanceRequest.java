package org.pms.silverocean.service.kyc;

import jakarta.validation.constraints.Size;

import java.time.ZonedDateTime;

public record KycDocumentMaintenanceRequest(
        ZonedDateTime issuedAt,
        ZonedDateTime expiresAt,
        ZonedDateTime reverificationDueAt,
        @Size(max = 500) String reason
) {}
