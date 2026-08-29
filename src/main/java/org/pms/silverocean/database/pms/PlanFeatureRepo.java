package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.PlanFeature;
import org.pms.silverocean.database.pms.entities.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanFeatureRepo extends JpaRepository<PlanFeature, Long> {
    List<PlanFeature> findBySubscriptionPlanAndActiveTrue(SubscriptionPlan subscriptionPlan);

    Optional<PlanFeature> findTopBySubscriptionPlanAndFeatureKeyOrderByIdDesc(SubscriptionPlan subscriptionPlan, String featureKey);

    void deleteBySubscriptionPlan(SubscriptionPlan subscriptionPlan);
}
