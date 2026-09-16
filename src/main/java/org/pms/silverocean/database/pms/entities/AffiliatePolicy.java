package org.pms.silverocean.database.pms.entities;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
@Entity @Table(name="pms_affiliate_policy") @Getter @Setter
public class AffiliatePolicy extends org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity implements Auditable {
 @Column(nullable=false,unique=true,length=32,updatable=false) private String policyKey="GLOBAL";
 @Column(nullable=false,precision=8,scale=2) private BigDecimal commissionRate;
 @Column(nullable=false) private int eligiblePaymentCount;
 @Column(nullable=false,precision=15,scale=2) private BigDecimal minimumPayout;
 @Column(nullable=false) private int holdDays;
 @Version private long version;
 public String toAuditJSON(){var n=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();n.put("commissionRate",commissionRate);n.put("eligiblePaymentCount",eligiblePaymentCount);n.put("minimumPayout",minimumPayout);n.put("holdDays",holdDays);n.put("version",version);return n.toString();}
}
