package org.pms.silverocean.database.pms;

import jakarta.persistence.LockModeType;
import org.pms.silverocean.database.pms.entities.AffiliateCommission;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface AffiliateCommissionRepo extends JpaRepository<AffiliateCommission, Long> {
    boolean existsByInvoiceId(long invoiceId);
    long countByReferredUserIdAndActiveTrue(long referredUserId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM AffiliateCommission c WHERE c.invoiceId=:invoiceId AND c.active=true")
    Optional<AffiliateCommission> findForUpdateByInvoiceId(long invoiceId);
    List<AffiliateCommission> findTop100ByAffiliateUserIdAndActiveTrueOrderByCreatedOnDesc(long id);
    List<AffiliateCommission> findTop200ByStatusAndActiveTrueAndAvailableAtLessThanEqualOrderByAvailableAtAsc(String status, ZonedDateTime availableAt);
    @Query("SELECT COALESCE(SUM(c.commissionAmount),0) FROM AffiliateCommission c WHERE c.affiliateUserId=:userId AND c.currency=:currency AND c.status=:status AND c.active=true")
    BigDecimal sumByStatus(long userId, String currency, String status);
    @Query("SELECT COALESCE(SUM(CASE WHEN c.status IN ('REVERSED','CLAWBACK_DUE','CLAWBACK_REQUESTED','CLAWBACK_SETTLED') THEN 0 ELSE c.commissionAmount END),0) FROM AffiliateCommission c WHERE c.affiliateUserId=:userId AND c.active=true")
    BigDecimal lifetime(long userId);
    @Modifying @Query("UPDATE AffiliateCommission c SET c.status='PAYOUT_REQUESTED',c.payoutId=:payoutId WHERE c.affiliateUserId=:userId AND c.currency=:currency AND c.status='EARNED' AND c.active=true")
    int claimEarned(long userId, String currency, long payoutId);
    @Modifying @Query("UPDATE AffiliateCommission c SET c.status='CLAWBACK_REQUESTED',c.payoutId=:payoutId WHERE c.affiliateUserId=:userId AND c.currency=:currency AND c.status='CLAWBACK_DUE' AND c.active=true")
    int claimClawbacks(long userId, String currency, long payoutId);
    @Modifying @Query("UPDATE AffiliateCommission c SET c.status='PAID' WHERE c.payoutId=:payoutId AND c.status='PAYOUT_REQUESTED' AND c.active=true")
    int settleEarned(long payoutId);
    @Modifying @Query("UPDATE AffiliateCommission c SET c.status='CLAWBACK_SETTLED' WHERE c.payoutId=:payoutId AND c.status='CLAWBACK_REQUESTED' AND c.active=true")
    int settleClawbacks(long payoutId);
    @Modifying @Query("UPDATE AffiliateCommission c SET c.status='EARNED',c.payoutId=null WHERE c.payoutId=:payoutId AND c.status='PAYOUT_REQUESTED' AND c.active=true")
    int releaseEarned(long payoutId);
    @Modifying @Query("UPDATE AffiliateCommission c SET c.status='CLAWBACK_DUE',c.payoutId=null WHERE c.payoutId=:payoutId AND c.status='CLAWBACK_REQUESTED' AND c.active=true")
    int releaseClawbacks(long payoutId);
    @Query("SELECT c FROM AffiliateCommission c WHERE c.active AND c.createdOn>=:start AND c.createdOn<:end AND (:privileged=true OR c.affiliateUserId=:userId) ORDER BY c.createdOn DESC")
    List<AffiliateCommission> findForReport(long userId, boolean privileged, ZonedDateTime start, ZonedDateTime end, Pageable pageable);
}
