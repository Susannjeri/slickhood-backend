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
import java.time.ZonedDateTime;

@Entity
@Table(name="pms_payment_operation",
        uniqueConstraints=@UniqueConstraint(name="uk_payment_operation_idempotency",columnNames="idempotencyKey"),
        indexes={@Index(name="idx_payment_operation_case",columnList="caseReference,occurredAt"),
                @Index(name="idx_payment_operation_payment",columnList="paymentId,occurredAt"),
                @Index(name="idx_payment_operation_invoice",columnList="invoiceId,occurredAt")})
@Getter @NoArgsConstructor
public class PaymentOperation extends BaseIDEntity {
    @Column(nullable=false,updatable=false,length=190) private String idempotencyKey;
    @Column(nullable=false,updatable=false,length=120) private String caseReference;
    @Column(nullable=false,updatable=false) private long paymentId;
    @Column(nullable=false,updatable=false) private long invoiceId;
    @Column(nullable=false,updatable=false,length=40) private String operationType;
    @Column(nullable=false,updatable=false,length=30) private String status;
    @Column(nullable=false,updatable=false,precision=19,scale=2) private BigDecimal amount;
    @Column(nullable=false,updatable=false,length=12) private String currency;
    @Column(updatable=false,length=50) private String provider;
    @Column(updatable=false,length=120) private String providerReference;
    @Column(updatable=false,length=1000) private String reason;
    @Column(nullable=false,updatable=false) private ZonedDateTime occurredAt;
    @Column(nullable=false,updatable=false) private long initiatedBy;

    public PaymentOperation(String idempotencyKey,String caseReference,long paymentId,long invoiceId,String operationType,
                            String status,BigDecimal amount,String currency,String provider,String providerReference,
                            String reason,ZonedDateTime occurredAt,long initiatedBy){
        this.idempotencyKey=idempotencyKey;this.caseReference=caseReference;this.paymentId=paymentId;this.invoiceId=invoiceId;
        this.operationType=operationType;this.status=status;this.amount=amount;this.currency=currency;this.provider=provider;
        this.providerReference=providerReference;this.reason=reason;this.occurredAt=occurredAt;this.initiatedBy=initiatedBy;
    }
}
