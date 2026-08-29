package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.controller.wrappers.ChargeDTO;
import org.pms.silverocean.database.pms.entities.base.BaseIDEntity;

@Table(name = "pms_unit_charge", indexes = {
        @Index(name = "idx_unit_charge_unitId", columnList = "unitId")
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UnitCharge extends BaseIDEntity {
    private long unitId;
    private long chargeId;
    private double amount;
    private String period;

    public UnitCharge(ChargeDTO chargeDTO) {
        this.chargeId = chargeDTO.chargeId();
        this.amount = chargeDTO.amount();
        this.period = chargeDTO.period().name();
    }
}
