package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.CommunityFund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CommunityFundRepo extends JpaRepository<CommunityFund,Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM CommunityFund f WHERE f.id=:id AND f.active=true")
    java.util.Optional<CommunityFund> findForUpdate(long id);
    boolean existsByPaymentAccountIdAndActiveTrueAndStatusIn(Long paymentAccountId,List<String> statuses);
    @Query("SELECT f FROM CommunityFund f JOIN Property p ON p.id=f.propertyId WHERE f.active AND (p.createdBy=:userId OR EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=f.propertyId AND pm.userId=:userId AND pm.active AND pm.roleName='ESTATE_MANAGER')) ORDER BY f.createdOn DESC")
    List<CommunityFund> findManaged(long userId);
    @Query("SELECT DISTINCT f FROM CommunityFund f JOIN CommunityFundContribution c ON c.fundId=f.id WHERE f.active AND c.active AND c.contributorUserId=:userId ORDER BY f.createdOn DESC")
    List<CommunityFund> findForContributor(long userId);
}
