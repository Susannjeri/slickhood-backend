package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.InsuranceCase;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface InsuranceCaseRepo extends JpaRepository<InsuranceCase,Long>{
 List<InsuranceCase> findAllByCustomerUserIdAndActiveTrueOrderByCreatedOnDesc(long userId);
 Optional<InsuranceCase> findByIdAndCustomerUserIdAndActiveTrue(long id,long userId);
 Page<InsuranceCase> findAllByAgencyIdAndActiveTrue(long agencyId,Pageable pageable);
 Page<InsuranceCase> findAllByAgencyIdAndStatusAndActiveTrue(long agencyId,String status,Pageable pageable);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from InsuranceCase c where c.id=:id and c.active=true") Optional<InsuranceCase> findByIdForUpdate(long id);
 long countByAgencyIdAndActiveTrueAndStatusNotIn(long agencyId,Collection<String> statuses);
 long countByAgencyIdAndActiveTrueAndAssignedAdviserIdIsNullAndStatusNotIn(long agencyId,Collection<String> statuses);
 boolean existsByReferenceAndActiveTrue(String reference);
 Page<InsuranceCase> findAllByActiveTrueAndStatusInAndSelectedAtBeforeAndPaymentReminderSentAtIsNull(Collection<String> statuses,java.time.LocalDateTime cutoff,Pageable pageable);
}
