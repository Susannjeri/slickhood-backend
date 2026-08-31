package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.InsurancePremiumPayment;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface InsurancePremiumPaymentRepo extends JpaRepository<InsurancePremiumPayment,Long>{
 List<InsurancePremiumPayment> findAllByCaseIdAndActiveTrueOrderByCreatedOnDesc(long caseId);
 List<InsurancePremiumPayment> findAllByCaseIdInAndActiveTrueOrderByCaseIdAscCreatedOnDesc(Collection<Long> caseIds);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from InsurancePremiumPayment p where p.id=:id and p.active=true") Optional<InsurancePremiumPayment> findByIdForUpdate(long id);
 long countByActiveTrueAndStatus(String status);
}
