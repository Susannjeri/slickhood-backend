package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.InsuranceRenewalOffer;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface InsuranceRenewalOfferRepo extends JpaRepository<InsuranceRenewalOffer,Long>{
 Optional<InsuranceRenewalOffer> findFirstByPolicyIdAndActiveTrueOrderByCreatedOnDesc(long policyId);
 List<InsuranceRenewalOffer> findAllByPolicyIdInAndActiveTrueOrderByCreatedOnDesc(Collection<Long> policyIds);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from InsuranceRenewalOffer o where o.id=:id and o.active=true") Optional<InsuranceRenewalOffer> findByIdForUpdate(long id);
}
