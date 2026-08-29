package org.pms.silverocean.service.visitor.wrappers;

import org.pms.silverocean.database.pms.entities.VisitorAccessEvent;

import java.time.ZonedDateTime;

public record AccessEventDTO(long id, Long visitorId, long propertyId, Long deviceId, String source,
                             String direction, String outcome, String reasonCode, String correlationId,
                             String vehiclePlate, ZonedDateTime occurredAt) {
    public AccessEventDTO(VisitorAccessEvent event) {
        this(event.getId(), event.getVisitorId(), event.getPropertyId(), event.getDeviceId(), event.getSource(),
                event.getDirection(), event.getOutcome(), event.getReasonCode(), event.getCorrelationId(),
                event.getVehiclePlate(), event.getOccurredAt());
    }
}
