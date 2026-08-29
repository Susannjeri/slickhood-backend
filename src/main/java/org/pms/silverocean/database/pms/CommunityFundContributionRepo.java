package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.CommunityFundContribution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommunityFundContributionRepo extends JpaRepository<CommunityFundContribution,Long> {
    List<CommunityFundContribution> findByFundIdAndActiveTrueOrderByCreatedOnAsc(Long fundId);
    List<CommunityFundContribution> findByFundIdAndContributorUserIdAndActiveTrue(Long fundId,Long contributorUserId);
    Optional<CommunityFundContribution> findByInvoiceIdAndActiveTrue(Long invoiceId);
    boolean existsByFundIdAndContributorUserIdAndUnitIdAndActiveTrue(Long fundId,Long contributorUserId,Long unitId);
}
