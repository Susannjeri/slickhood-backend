package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.InsuranceRenewalPayment;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface InsuranceRenewalPaymentRepo extends JpaRepository<InsuranceRenewalPayment,Long>{
 Optional<InsuranceRenewalPayment> findFirstByRenewalOfferIdAndActiveTrueOrderByCreatedOnDesc(long offerId);
 List<InsuranceRenewalPayment> findAllByRenewalOfferIdInAndActiveTrueOrderByCreatedOnDesc(Collection<Long> offerIds);
 Optional<InsuranceRenewalPayment> findByIdAndActiveTrue(long id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from InsuranceRenewalPayment p where p.id=:id and p.active=true") Optional<InsuranceRenewalPayment> findByIdForUpdate(long id);
}
