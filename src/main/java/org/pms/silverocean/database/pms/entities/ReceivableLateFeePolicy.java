package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "pms_receivable_late_fee_policy", uniqueConstraints =
        @UniqueConstraint(name = "uk_receivable_late_fee_policy_biller_type", columnNames = {"createdBy", "billingType"}))
@Getter
@Setter
@NoArgsConstructor
public class ReceivableLateFeePolicy extends BaseCreatorEntity {
    @Column(nullable = false, length = 40)
    private String billingType;
    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal percentageRate = BigDecimal.ZERO;
    @Column(nullable = false)
    private int graceDays;
    @Column(nullable = false)
    private LocalDate effectiveFrom;
    @Column(nullable = false)
    private boolean enabled;
}
