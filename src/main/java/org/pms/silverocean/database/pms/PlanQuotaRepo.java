package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.PlanQuota;
import org.pms.silverocean.database.pms.entities.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanQuotaRepo extends JpaRepository<PlanQuota, Long> {
    List<PlanQuota> findBySubscriptionPlanAndActiveTrue(SubscriptionPlan subscriptionPlan);

    Optional<PlanQuota> findTopBySubscriptionPlanAndMetricKeyOrderByIdDesc(SubscriptionPlan subscriptionPlan, String metricKey);

    void deleteBySubscriptionPlan(SubscriptionPlan subscriptionPlan);
}
