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
            BigDecimal minimumPayout,String currency,Long payoutAccountId) {
        public Profile(AffiliateProfile p){this(p.getReferralCode(),p.getStatus(),p.getCommissionRate(),p.getMinimumPayout(),p.getCurrency(),p.getPayoutAccountId());}
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
    public record AdminPayout(long id,String payoutNumber,long affiliateUserId,BigDecimal amount,String currency,
            String status,ZonedDateTime requestedAt,ZonedDateTime processedAt,String paymentReference,String notes,
            String payoutAccountName,String payoutChannel) {
        public AdminPayout(AffiliatePayout p){this(p.getId(),p.getPayoutNumber(),p.getAffiliateUserId(),p.getAmount(),p.getCurrency(),p.getStatus(),p.getRequestedAt(),p.getProcessedAt(),p.getPaymentReference(),p.getNotes(),p.getPayoutAccountName(),p.getPayoutChannel());}
    }
    public record Dashboard(Profile profile,long totalReferrals,long conversions,BigDecimal conversionRatePercent,
            BigDecimal availableBalance,BigDecimal pendingEarnings,BigDecimal lifetimeEarnings,BigDecimal pendingPayouts,
            List<Referral> referrals,List<Commission> commissions,List<Payout> payouts,boolean historyLimited) {}
    public record PayoutAccount(@NotNull Long paymentAccountId) {}
    public record PayoutDecision(@NotBlank String status,@Size(max=100) String paymentReference,@Size(max=1000) String notes) {}
}
