package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.SubscriptionPlan;
import org.pms.silverocean.service.subscription.enums.PlanCategory;
import org.pms.silverocean.service.subscription.enums.BillingCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepo extends JpaRepository<SubscriptionPlan, Long>, JpaSpecificationExecutor<SubscriptionPlan> {
    Optional<SubscriptionPlan> findByCodeAndActiveTrue(String code);
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, Long id);
    boolean existsByRoleFamilyAndBillingCycleAndDisplayNameIgnoreCaseAndActiveTrue(
            PMSRole roleFamily, BillingCycle billingCycle, String displayName);
    boolean existsByRoleFamilyAndBillingCycleAndDisplayNameIgnoreCaseAndActiveTrueAndIdNot(
            PMSRole roleFamily, BillingCycle billingCycle, String displayName, Long id);
    Optional<SubscriptionPlan> findByCode(String code);
    boolean existsByPlanCategory(PlanCategory planCategory);

    List<SubscriptionPlan> findByRoleFamilyAndActiveTrueOrderByPriceAsc(PMSRole roleFamily);
}
