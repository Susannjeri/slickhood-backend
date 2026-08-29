package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;import lombok.Getter;import lombok.NoArgsConstructor;import org.pms.silverocean.database.pms.entities.base.BaseIDEntity;
import java.math.BigDecimal;import java.time.ZonedDateTime;

@Entity @Table(name="pms_lease_financial_event",uniqueConstraints=@UniqueConstraint(name="uk_lease_fin_event_idempotency",columnNames="idempotencyKey"),indexes={@Index(name="idx_lease_fin_event_lease",columnList="leaseId,occurredAt"),@Index(name="idx_lease_fin_event_invoice",columnList="invoiceId")})
@Getter @NoArgsConstructor
public class LeaseFinancialEvent extends BaseIDEntity {
 @Column(nullable=false,updatable=false,length=190)private String idempotencyKey;@Column(nullable=false,updatable=false)private long leaseId;@Column(updatable=false)private Long invoiceId;
 @Column(nullable=false,updatable=false,length=40)private String eventType;@Column(nullable=false,updatable=false,precision=19,scale=2)private BigDecimal amount;@Column(nullable=false,updatable=false,length=12)private String currency;
 @Column(updatable=false,length=120)private String externalReference;@Column(updatable=false,length=1000)private String reason;@Column(nullable=false,updatable=false)private ZonedDateTime occurredAt;@Column(nullable=false,updatable=false)private long initiatedBy;
 public LeaseFinancialEvent(String key,long leaseId,Long invoiceId,String type,BigDecimal amount,String currency,String reference,String reason,ZonedDateTime occurredAt,long initiatedBy){this.idempotencyKey=key;this.leaseId=leaseId;this.invoiceId=invoiceId;this.eventType=type;this.amount=amount;this.currency=currency;this.externalReference=reference;this.reason=reason;this.occurredAt=occurredAt;this.initiatedBy=initiatedBy;}
}
