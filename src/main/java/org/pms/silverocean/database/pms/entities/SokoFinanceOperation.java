package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.pms.silverocean.database.pms.entities.base.BaseIDEntity;

import java.math.BigDecimal;

/** Immutable provider-confirmed Soko adjustment used to reject callback replays and conflicts. */
@Entity
@Table(name="pms_soko_finance_operation",
        uniqueConstraints=@UniqueConstraint(name="uk_soko_finance_provider_ref",columnNames={"orderId","providerReference"}),
        indexes=@Index(name="idx_soko_finance_order",columnList="orderId,createdOn"))
@Getter
@NoArgsConstructor
public class SokoFinanceOperation extends BaseIDEntity {
    @Column(nullable=false,updatable=false) private long orderId;
    @Column(nullable=false,updatable=false,length=40) private String operationType;
    @Column(nullable=false,updatable=false,length=120) private String providerReference;
    @Column(nullable=false,updatable=false,precision=19,scale=2) private BigDecimal amount;

    public SokoFinanceOperation(long orderId,String operationType,String providerReference,BigDecimal amount){
        this.orderId=orderId;this.operationType=operationType;this.providerReference=providerReference;this.amount=amount;
    }
}
