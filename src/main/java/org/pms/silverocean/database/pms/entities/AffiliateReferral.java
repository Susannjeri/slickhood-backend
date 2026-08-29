package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;import jakarta.persistence.Index;import jakarta.persistence.Table;import lombok.Getter;import lombok.NoArgsConstructor;import lombok.Setter;import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;import java.time.ZonedDateTime;

@Entity @Table(name="pms_affiliate_referral",indexes={@Index(name="idx_referral_affiliate",columnList="affiliateUserId,status"),@Index(name="idx_referral_referred",columnList="referredUserId",unique=true)})
@Getter @Setter @NoArgsConstructor
public class AffiliateReferral extends BaseCreatorEntity {
    private long affiliateUserId;private long referredUserId;private String referralCode;private String status;private String campaign;private ZonedDateTime registeredAt;private ZonedDateTime convertedAt;
}
