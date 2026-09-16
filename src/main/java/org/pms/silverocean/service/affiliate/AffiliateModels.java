package org.pms.silverocean.service.affiliate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.pms.silverocean.database.pms.entities.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public final class AffiliateModels {
    private AffiliateModels() {}
    public record PublicReferral(boolean valid) {}
    public record Profile(String referralCode,String status,BigDecimal commissionRate,
            BigDecimal minimumPayout,String currency,Long payoutAccountId,ZonedDateTime appliedAt,
            ZonedDateTime reviewedAt,Long reviewedByUserId,String reviewNotes) {
        public Profile(AffiliateProfile p){this(p.getReferralCode(),p.getStatus(),p.getCommissionRate(),p.getMinimumPayout(),p.getCurrency(),p.getPayoutAccountId(),
                p.getCreatedOn(),p.getReviewedAt(),p.getReviewedByUserId(),p.getReviewNotes());}
    }
    public record Referral(long id,String status,String campaign,ZonedDateTime registeredAt,ZonedDateTime convertedAt) {
        public Referral(AffiliateReferral r){this(r.getId(),r.getStatus(),r.getCampaign(),r.getRegisteredAt(),r.getConvertedAt());}
    }
    public record Commission(long id,String invoiceRef,BigDecimal qualifyingAmount,BigDecimal commissionRate,
            BigDecimal commissionAmount,String currency,String status,ZonedDateTime earnedAt,ZonedDateTime availableAt) {
        public Commission(AffiliateCommission c){this(c.getId(),c.getInvoiceRef(),c.getQualifyingAmount(),c.getCommissionRate(),c.getCommissionAmount(),c.getCurrency(),c.getStatus(),c.getEarnedAt(),c.getAvailableAt());}
    }
    public record Payout(long id,String payoutNumber,BigDecimal amount,String currency,String status,
            ZonedDateTime requestedAt,ZonedDateTime processedAt,String paymentReference,String notes) {
        public Payout(AffiliatePayout p){this(p.getId(),p.getPayoutNumber(),p.getAmount(),p.getCurrency(),p.getStatus(),p.getRequestedAt(),p.getProcessedAt(),p.getPaymentReference(),p.getNotes());}
    }
    public record AdminPayout(long id,String payoutNumber,long affiliateUserId,Long paymentAccountId,BigDecimal amount,String currency,
            String status,ZonedDateTime requestedAt,ZonedDateTime processedAt,String paymentReference,String notes,
            String payoutAccountName,String payoutChannel,long version) {
        public AdminPayout(AffiliatePayout p){this(p.getId(),p.getPayoutNumber(),p.getAffiliateUserId(),p.getPaymentAccountId(),p.getAmount(),p.getCurrency(),p.getStatus(),p.getRequestedAt(),p.getProcessedAt(),p.getPaymentReference(),p.getNotes(),p.getPayoutAccountName(),p.getPayoutChannel(),p.getVersion());}
    }
    public record Dashboard(Profile profile,long totalReferrals,long conversions,BigDecimal conversionRatePercent,
            BigDecimal availableBalance,BigDecimal pendingEarnings,BigDecimal lifetimeEarnings,BigDecimal pendingPayouts,
            List<Referral> referrals,List<Commission> commissions,List<Payout> payouts,boolean historyLimited,RewardTerms rewardTerms) {}
    public record RewardTerms(int eligiblePaymentCount,int holdDays) {}
    public record PayoutAccount(@NotNull Long paymentAccountId) {}
    public record PayoutDecision(@NotBlank String status,@Size(max=100) String paymentReference,@Size(max=1000) String notes,
                                 @NotNull @jakarta.validation.constraints.DecimalMin(value="0",inclusive=false) BigDecimal expectedAmount,
                                 @NotBlank @jakarta.validation.constraints.Pattern(regexp="[A-Z]{3}") String expectedCurrency,
                                 @NotNull Long expectedVersion) {}
}
