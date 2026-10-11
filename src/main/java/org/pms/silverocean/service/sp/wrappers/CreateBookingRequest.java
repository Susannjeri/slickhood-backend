package org.pms.silverocean.service.sp.wrappers;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateBookingRequest(
    @NotNull @Positive Long serviceId,
    @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime scheduledAt,
    @Size(max=2000) String notes,
    @Positive Long propertyId,
    @Positive Long unitId,
    @Size(max=500) String serviceAddress,
    @Size(max=100) String idempotencyKey
) {
    public CreateBookingRequest(Long serviceId, LocalDateTime scheduledAt, String notes, Long propertyId, Long unitId) {
        this(serviceId, scheduledAt, notes, propertyId, unitId, null, null);
    }
    public CreateBookingRequest(Long serviceId, LocalDateTime scheduledAt, String notes, Long propertyId, Long unitId, String idempotencyKey) {
        this(serviceId, scheduledAt, notes, propertyId, unitId, null, idempotencyKey);
    }
}
