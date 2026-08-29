package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseActiveEntity;

import java.time.LocalDate;

@Table(name = "pms_lease_charge", indexes = {
        @Index(name = "idx_lease_charge_leaseId", columnList = "leaseId")
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LeaseCharge extends BaseActiveEntity {
    private long leaseId;
    private long chargeId;
    private double amount;
    private String period;
    private LocalDate nextPaymentDate;
}
