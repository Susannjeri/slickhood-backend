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
 @org.springframework.data.jpa.repository.Query("select p from InsurancePolicy p join InsuranceCase c on p.caseId=c.id where c.agencyId=:agencyId and p.active=true and p.endDate between :from and :to") Page<InsurancePolicy> findRenewalsByAgencyId(long agencyId,LocalDate from,LocalDate to,Pageable pageable);
 Optional<InsurancePolicy> findByCaseIdAndActiveTrue(long caseId);
 long countByActiveTrueAndEndDateBetween(LocalDate from,LocalDate to);
 @org.springframework.data.jpa.repository.Query("select count(p) from InsurancePolicy p join InsuranceCase c on p.caseId=c.id where c.agencyId=:agencyId and p.active=true and p.endDate between :from and :to") long countRenewalsByAgencyId(long agencyId,LocalDate from,LocalDate to);
 boolean existsByPolicyNumberAndActiveTrue(String policyNumber);
 Page<InsurancePolicy> findAllByActiveTrueAndRenewalStatusAndEndDateBetweenAndRenewalReminderSentAtIsNull(String status,LocalDate from,LocalDate to,Pageable pageable);
 @org.springframework.data.jpa.repository.Query("select p from InsurancePolicy p join InsuranceCase c on p.caseId=c.id where c.agencyId=:agencyId and p.active=true and p.renewalStatus=:status and p.endDate between :from and :to and p.renewalReminderSentAt is null") Page<InsurancePolicy> findReminderQueueByAgencyId(long agencyId,String status,LocalDate from,LocalDate to,Pageable pageable);
}
