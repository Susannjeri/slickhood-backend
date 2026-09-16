package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;import jakarta.persistence.Index;import jakarta.persistence.Table;import jakarta.persistence.Version;import lombok.Getter;import lombok.NoArgsConstructor;import lombok.Setter;import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;import java.math.BigDecimal;import java.time.ZonedDateTime;

@Entity @Table(name="pms_affiliate_payout",indexes=@Index(name="idx_payout_affiliate",columnList="affiliateUserId,status"))
@Getter @Setter @NoArgsConstructor
public class AffiliatePayout extends BaseCreatorEntity implements Auditable {
    private String payoutNumber;private long affiliateUserId;private Long paymentAccountId;private String payoutAccountName;private String payoutChannel;private BigDecimal amount;private String currency;private String status;private ZonedDateTime requestedAt;private ZonedDateTime processedAt;private Long processedByUserId;private String paymentReference;private String paymentReferenceKey;private String notes;
    @Version private long version;
    @Override public String toAuditJSON(){var json=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();json.put("id",getId());json.put("affiliateUserId",affiliateUserId);json.put("payoutNumber",payoutNumber);json.put("paymentAccountId",paymentAccountId);json.put("payoutChannel",payoutChannel);json.put("amount",amount);json.put("currency",currency);json.put("status",status);json.put("paymentReference",paymentReference);json.put("processedByUserId",processedByUserId);json.put("version",version);return json.toString();}
}
