package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.controller.wrappers.ChargeDTO;
import org.pms.silverocean.database.pms.entities.base.BaseIDEntity;
import java.math.BigDecimal;

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
    @Column(name = "amount_decimal", precision = 19, scale = 2)
    private BigDecimal amountDecimal;
    private String period;

    public UnitCharge(ChargeDTO chargeDTO) {
        this.chargeId = chargeDTO.chargeId();
        this.amount = chargeDTO.amount();
        this.period = chargeDTO.period().name();
    }

    public BigDecimal moneyAmount() { return amountDecimal == null
            ? org.pms.silverocean.service.payment.money.MonetaryPolicy.amount(amount)
            : org.pms.silverocean.service.payment.money.MonetaryPolicy.amount(amountDecimal); }
    public void setMoneyAmount(BigDecimal value) { amountDecimal=org.pms.silverocean.service.payment.money.MonetaryPolicy.amount(value);amount=amountDecimal.doubleValue(); }
    @PostLoad private void readAmountShadow(){if(amountDecimal!=null)amount=amountDecimal.doubleValue();}
    @PrePersist @PreUpdate private void writeAmountShadow(){amountDecimal=org.pms.silverocean.service.payment.money.MonetaryPolicy.amount(amount);}
}
