package org.pms.silverocean.service.sp.wrappers;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateBookingRequest(
    @NotNull @Positive Long serviceId,
    @NotNull @Future @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime scheduledAt,
    @Size(max=2000) String notes,
    @Positive Long propertyId,
    @Positive Long unitId
) {}
