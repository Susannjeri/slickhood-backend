package org.pms.silverocean.service.affiliate;

import jakarta.validation.constraints.NotNull;import java.math.BigDecimal;import java.util.List;import org.pms.silverocean.database.pms.entities.AffiliateCommission;import org.pms.silverocean.database.pms.entities.AffiliatePayout;import org.pms.silverocean.database.pms.entities.AffiliateProfile;import org.pms.silverocean.database.pms.entities.AffiliateReferral;

public final class AffiliateModels {private AffiliateModels(){}
 public record Dashboard(AffiliateProfile profile,long totalReferrals,long conversions,BigDecimal availableBalance,BigDecimal lifetimeEarnings,BigDecimal pendingPayouts,List<AffiliateReferral> referrals,List<AffiliateCommission> commissions,List<AffiliatePayout> payouts){}
 public record PayoutAccount(@NotNull Long paymentAccountId){}
 public record PayoutDecision(String status,String paymentReference,String notes){}
}
