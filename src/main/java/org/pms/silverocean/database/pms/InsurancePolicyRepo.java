package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.InsurancePolicy;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;
public interface InsurancePolicyRepo extends JpaRepository<InsurancePolicy,Long>{
 List<InsurancePolicy> findAllByCustomerUserIdAndActiveTrueOrderByEndDateDesc(long userId);
 Optional<InsurancePolicy> findByIdAndCustomerUserIdAndActiveTrue(long id,long userId);
 Page<InsurancePolicy> findAllByActiveTrueAndEndDateBetween(LocalDate from,LocalDate to,Pageable pageable);
 Optional<InsurancePolicy> findByCaseIdAndActiveTrue(long caseId);
 long countByActiveTrueAndEndDateBetween(LocalDate from,LocalDate to);
 boolean existsByPolicyNumberAndActiveTrue(String policyNumber);
 Page<InsurancePolicy> findAllByActiveTrueAndRenewalStatusAndEndDateBetweenAndRenewalReminderSentAtIsNull(String status,LocalDate from,LocalDate to,Pageable pageable);
}
