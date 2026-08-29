package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import org.pms.silverocean.service.sales.SaleStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pms_sale_transaction", indexes = {
        @Index(name = "idx_sale_agent", columnList = "salesAgentUserId, active"),
        @Index(name = "idx_sale_buyer", columnList = "buyerUserId, active"),
        @Index(name = "idx_sale_property", columnList = "propertyId, unitId, active")})
@Getter @Setter @NoArgsConstructor
public class SaleTransaction extends BaseCreatorEntity {
    private long propertyId;
    private Long unitId;
    private long salesAgentUserId;
    private long buyerUserId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private SaleStatus status;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal askingPrice;
    @Column(precision = 19, scale = 2) private BigDecimal offerAmount;
    private String currency;
    private LocalDateTime offerAcceptedAt;
    private LocalDateTime completedAt;
    @Column(length = 1000) private String notes;
}
