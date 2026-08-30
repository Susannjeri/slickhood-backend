package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
@Table(name = "pms_property_ownership", indexes = {
        @Index(name = "idx_ownership_homeowner", columnList = "homeownerUserId, active"),
        @Index(name = "idx_ownership_property_unit", columnList = "propertyId, unitId, active")})
@Getter @Setter @NoArgsConstructor
public class PropertyOwnership extends BaseCreatorEntity {
    private long propertyId;
    private Long unitId;
    private long homeownerUserId;
    @Column(nullable = false) private LocalDate ownershipStart;
    private LocalDate ownershipEnd;
    private String source;
    private Long sourceSaleTransactionId;
    @Column(length = 500) private String terminationReason;
    private Long terminatedBy;
    private ZonedDateTime terminatedAt;
}
