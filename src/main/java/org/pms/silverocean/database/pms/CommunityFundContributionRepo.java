package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.CommunityFundContribution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommunityFundContributionRepo extends JpaRepository<CommunityFundContribution,Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT c FROM CommunityFundContribution c WHERE c.id=:id AND c.active=true")
    Optional<CommunityFundContribution> findForUpdate(long id);
    List<CommunityFundContribution> findByFundIdAndActiveTrueOrderByCreatedOnAsc(Long fundId);
    List<CommunityFundContribution> findByFundIdAndContributorUserIdAndActiveTrue(Long fundId,Long contributorUserId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<CommunityFundContribution> findByInvoiceIdAndActiveTrue(Long invoiceId);
    boolean existsByFundIdAndContributorUserIdAndUnitIdAndActiveTrue(Long fundId,Long contributorUserId,Long unitId);
}
