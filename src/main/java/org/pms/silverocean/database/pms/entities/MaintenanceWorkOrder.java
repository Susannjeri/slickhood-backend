package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name="pms_maintenance_work_order",indexes={@Index(name="idx_maintenance_unit_status",columnList="unitId,status,active"),@Index(name="idx_maintenance_property_status",columnList="propertyId,status,active")})
@Getter @Setter @NoArgsConstructor
public class MaintenanceWorkOrder extends BaseCreatorEntity {
 @Column(nullable=false,unique=true,length=40) private String workOrderNumber;
 private long propertyId; private long unitId; private long requestedByUserId; private Long assignedProviderServiceId;
 @Column(nullable=false,length=120) private String title;
 @Column(nullable=false,length=2000) private String description;
 @Column(nullable=false,length=40) private String category;
 @Column(nullable=false,length=20) private String priority;
 @Column(nullable=false,length=30) private String status;
 private ZonedDateTime scheduledAt; private ZonedDateTime completedAt;
 @Column(precision=19,scale=2) private BigDecimal estimatedCost;
 @Column(precision=19,scale=2) private BigDecimal actualCost;
 private String currency;
 @Column(length=1000) private String resolutionNotes;
}
