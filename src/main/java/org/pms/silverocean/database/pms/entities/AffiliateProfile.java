package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity @Table(name="pms_affiliate_profile",indexes={@Index(name="idx_affiliate_user",columnList="userId",unique=true),@Index(name="idx_affiliate_code",columnList="referralCode",unique=true)})
@Getter @Setter @NoArgsConstructor
public class AffiliateProfile extends BaseCreatorEntity implements Auditable {
    private long userId;
    private String referralCode;
    private String status;
    private BigDecimal commissionRate;
    private BigDecimal minimumPayout;
    private String currency;
    private Long payoutAccountId;
    private ZonedDateTime reviewedAt;
    private Long reviewedByUserId;
    private String reviewNotes;
    @Override public String toAuditJSON() {
        var node=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        node.put("id",getId());node.put("userId",userId);node.put("status",status);node.put("commissionRate",commissionRate);
        node.put("minimumPayout",minimumPayout);node.put("currency",currency);node.put("payoutAccountId",payoutAccountId);
        node.put("reviewedAt",reviewedAt==null?null:reviewedAt.toString());node.put("reviewedByUserId",reviewedByUserId);
        node.put("reviewNotes",reviewNotes);return node.toString();
    }
}
