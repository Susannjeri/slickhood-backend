package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Collection;

/** Bounded administrative and owner-only history queries. Controllers/services enforce access. */
public interface AffiliateQueryRepo extends Repository<AffiliateProfile, Long> {
    @Query("SELECT p FROM AffiliateProfile p JOIN Users u ON u.id=p.userId WHERE p.active=true " +
            "AND (:status IS NULL OR p.status=:status) AND (:query IS NULL OR LOWER(u.fullName) LIKE :query " +
            "OR LOWER(u.email) LIKE :query OR LOWER(p.referralCode) LIKE :query)")
    Page<AffiliateProfile> directory(String query, String status, Pageable pageable);

    interface ReferralCount {
        Long getAffiliateUserId(); long getReferrals(); long getConversions();
    }
    @Query("SELECT r.affiliateUserId AS affiliateUserId, COUNT(r.id) AS referrals, " +
            "SUM(CASE WHEN r.status='CONVERTED' THEN 1 ELSE 0 END) AS conversions " +
            "FROM AffiliateReferral r WHERE r.active=true AND r.affiliateUserId IN :userIds GROUP BY r.affiliateUserId")
    List<ReferralCount> referralCounts(Collection<Long> userIds);

    interface CurrencyBalance {
        String getCurrency(); BigDecimal getPending(); BigDecimal getEarned(); BigDecimal getClawback(); BigDecimal getLifetime();
    }
    @Query("SELECT c.currency AS currency, " +
            "SUM(CASE WHEN c.status='PENDING' THEN c.commissionAmount ELSE 0 END) AS pending, " +
            "SUM(CASE WHEN c.status='EARNED' THEN c.commissionAmount ELSE 0 END) AS earned, " +
            "SUM(CASE WHEN c.status='CLAWBACK_DUE' THEN c.commissionAmount ELSE 0 END) AS clawback, " +
            "SUM(CASE WHEN c.status IN ('REVERSED','CLAWBACK_DUE','CLAWBACK_REQUESTED','CLAWBACK_SETTLED') THEN 0 ELSE c.commissionAmount END) AS lifetime " +
            "FROM AffiliateCommission c WHERE c.affiliateUserId=:userId AND c.active=true GROUP BY c.currency")
    List<CurrencyBalance> balances(long userId);
    @Query("SELECT COALESCE(SUM(p.amount),0) FROM AffiliatePayout p WHERE p.affiliateUserId=:userId AND p.currency=:currency " +
            "AND p.active=true AND p.status IN ('REQUESTED','PROCESSING')")
    BigDecimal pendingPayouts(long userId, String currency);
    @Query("SELECT COALESCE(SUM(c.commissionAmount),0) FROM AffiliateCommission c WHERE c.affiliateUserId=:userId AND c.currency=:currency " +
            "AND c.active=true AND c.status NOT IN ('REVERSED','CLAWBACK_DUE','CLAWBACK_REQUESTED','CLAWBACK_SETTLED')")
    BigDecimal lifetime(long userId, String currency);
    @Query("SELECT c FROM AffiliateCommission c WHERE c.affiliateUserId=:userId AND c.active=true")
    Page<AffiliateCommission> commissions(long userId, Pageable pageable);
    @Query("SELECT r FROM AffiliateReferral r WHERE r.affiliateUserId=:userId AND r.active=true")
    Page<AffiliateReferral> referrals(long userId, Pageable pageable);
    @Query("SELECT p FROM AffiliatePayout p WHERE p.affiliateUserId=:userId AND p.active=true")
    Page<AffiliatePayout> payouts(long userId, Pageable pageable);
    @Query("SELECT p FROM AffiliatePayout p JOIN Users u ON u.id=p.affiliateUserId WHERE p.active=true " +
            "AND (:status IS NULL OR p.status=:status) AND (:query IS NULL OR LOWER(p.payoutNumber) LIKE :query " +
            "OR LOWER(u.fullName) LIKE :query OR LOWER(u.email) LIKE :query)")
    Page<AffiliatePayout> payoutQueue(String query, String status, Pageable pageable);
}
