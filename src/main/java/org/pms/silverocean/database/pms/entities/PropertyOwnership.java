package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import java.time.LocalDate;

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
}
