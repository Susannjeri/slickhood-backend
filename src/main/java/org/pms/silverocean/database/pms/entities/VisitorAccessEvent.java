package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

import java.time.ZonedDateTime;

@Entity
@Table(name = "pms_visitor_access_event", indexes = {
        @Index(name = "idx_access_event_visitor", columnList = "visitorId,occurredAt"),
        @Index(name = "idx_access_event_property", columnList = "propertyId,occurredAt"),
        @Index(name = "idx_access_event_correlation", columnList = "correlationId", unique = true)
})
@Getter @Setter @NoArgsConstructor
public class VisitorAccessEvent extends BaseCreatorEntity {
    private Long visitorId;
    private long propertyId;
    private Long deviceId;
    private String source;
    private String direction;
    private String outcome;
    private String reasonCode;
    private String correlationId;
    private String vehiclePlate;
    private ZonedDateTime occurredAt;
}
