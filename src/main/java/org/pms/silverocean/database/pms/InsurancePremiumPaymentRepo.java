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
 @Query("select count(p) from InsurancePremiumPayment p join InsuranceCase c on p.caseId=c.id where c.agencyId=:agencyId and p.active=true and p.status=:status") long countByAgencyIdAndStatus(long agencyId,String status);
}
