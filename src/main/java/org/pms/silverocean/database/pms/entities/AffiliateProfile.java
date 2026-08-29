package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import java.math.BigDecimal;

@Entity @Table(name="pms_affiliate_profile",indexes={@Index(name="idx_affiliate_user",columnList="userId",unique=true),@Index(name="idx_affiliate_code",columnList="referralCode",unique=true)})
@Getter @Setter @NoArgsConstructor
public class AffiliateProfile extends BaseCreatorEntity {
    private long userId;
    private String referralCode;
    private String status;
    private BigDecimal commissionRate;
    private BigDecimal minimumPayout;
    private String currency;
    private Long payoutAccountId;
}
