package org.pms.silverocean.service.estate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record OwnershipRequest(@Positive long propertyId, @Positive Long unitId, @Positive long homeownerUserId,
                               @NotNull LocalDate ownershipStart, @jakarta.validation.constraints.Size(max = 255) String source) {}
