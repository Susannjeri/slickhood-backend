package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;import jakarta.persistence.Index;import jakarta.persistence.Table;import lombok.Getter;import lombok.NoArgsConstructor;import lombok.Setter;import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;import java.math.BigDecimal;import java.time.ZonedDateTime;

@Entity @Table(name="pms_affiliate_commission",indexes={@Index(name="idx_commission_affiliate",columnList="affiliateUserId,status"),@Index(name="idx_commission_invoice",columnList="invoiceId",unique=true)})
@Getter @Setter @NoArgsConstructor
public class AffiliateCommission extends BaseCreatorEntity {
    private long affiliateUserId;private long referredUserId;private long invoiceId;private String invoiceRef;private BigDecimal qualifyingAmount;private BigDecimal commissionRate;private BigDecimal commissionAmount;private String currency;private String status;private String providerReference;private ZonedDateTime earnedAt;private Long payoutId;private int eligibleSequence;private ZonedDateTime reversedAt;private String reversalReason;
}
