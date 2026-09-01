package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.InsuranceRenewalReminder;
import org.springframework.data.jpa.repository.JpaRepository;
public interface InsuranceRenewalReminderRepo extends JpaRepository<InsuranceRenewalReminder,Long>{boolean existsByPolicyIdAndPolicyEndDateAndReminderDays(long policyId,java.time.LocalDate policyEndDate,int reminderDays);}
