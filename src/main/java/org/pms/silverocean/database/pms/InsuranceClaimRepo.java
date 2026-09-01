package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.InsuranceClaim;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface InsuranceClaimRepo extends JpaRepository<InsuranceClaim,Long>{
 List<InsuranceClaim> findAllByCustomerUserIdAndActiveTrueOrderByCreatedOnDesc(long userId);
 Optional<InsuranceClaim> findByIdAndCustomerUserIdAndActiveTrue(long id,long userId);
 Page<InsuranceClaim> findAllByActiveTrue(Pageable pageable);
 Page<InsuranceClaim> findAllByStatusAndActiveTrue(String status,Pageable pageable);
 @Query("select cl from InsuranceClaim cl join InsurancePolicy p on cl.policyId=p.id join InsuranceCase c on p.caseId=c.id where c.agencyId=:agencyId and cl.active=true") Page<InsuranceClaim> findQueueByAgencyId(long agencyId,Pageable pageable);
 @Query("select cl from InsuranceClaim cl join InsurancePolicy p on cl.policyId=p.id join InsuranceCase c on p.caseId=c.id where c.agencyId=:agencyId and cl.status=:status and cl.active=true") Page<InsuranceClaim> findQueueByAgencyIdAndStatus(long agencyId,String status,Pageable pageable);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from InsuranceClaim c where c.id=:id and c.active=true") Optional<InsuranceClaim> findByIdForUpdate(long id);
 long countByActiveTrueAndStatusNotIn(Collection<String> statuses);
 @Query("select count(cl) from InsuranceClaim cl join InsurancePolicy p on cl.policyId=p.id join InsuranceCase c on p.caseId=c.id where c.agencyId=:agencyId and cl.active=true and cl.status not in :statuses") long countOpenByAgencyId(long agencyId,Collection<String> statuses);
 boolean existsByReferenceAndActiveTrue(String reference);
}
