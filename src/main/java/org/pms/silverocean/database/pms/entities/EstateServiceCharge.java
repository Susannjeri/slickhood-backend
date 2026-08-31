package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name="pms_estate_service_charge", indexes={
 @Index(name="idx_charge_homeowner",columnList="homeownerUserId, active"),@Index(name="idx_charge_property",columnList="propertyId, active")})
@Getter @Setter @NoArgsConstructor
public class EstateServiceCharge extends BaseCreatorEntity {
 private long propertyId; private long unitId; private long homeownerUserId; private long invoiceId;
 @Column(nullable=false,precision=19,scale=2) private BigDecimal amount; private String currency;
 @Column(nullable=false) private LocalDate dueDate; private String description;
 private LocalDateTime preDueReminderQueuedAt; private LocalDateTime overdueNoticeQueuedAt;
 private LocalDateTime lastOverdueReminderQueuedAt;
 @Column(nullable=false) private int overdueReminderCount;
}
