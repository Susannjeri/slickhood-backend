package org.pms.silverocean.database.pms;

import jakarta.persistence.LockModeType;
import org.pms.silverocean.database.pms.entities.AffiliateReferral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface AffiliateReferralRepo extends JpaRepository<AffiliateReferral, Long> {
    Optional<AffiliateReferral> findByReferredUserIdAndActiveTrue(long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM AffiliateReferral r WHERE r.referredUserId=:userId AND r.active=true")
    Optional<AffiliateReferral> findForCommissionByReferredUserId(long userId);
    List<AffiliateReferral> findTop100ByAffiliateUserIdAndActiveTrueOrderByCreatedOnDesc(long affiliateUserId);
    long countByAffiliateUserIdAndActiveTrue(long id);
    long countByAffiliateUserIdAndStatusAndActiveTrue(long id, String status);
}
