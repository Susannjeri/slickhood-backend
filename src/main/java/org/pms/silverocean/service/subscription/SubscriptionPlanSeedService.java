package org.pms.silverocean.service.subscription;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.database.pms.SubscriptionPlanRepo;
import org.pms.silverocean.database.pms.entities.SubscriptionPlan;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.subscription.enums.BillingCycle;
import org.pms.silverocean.service.subscription.enums.PlanCategory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Component
@Slf4j
public class SubscriptionPlanSeedService {
    private final SubscriptionPlanRepo subscriptionPlanRepo;
    private final String defaultCurrency;
    private final String serviceProviderStandardTier;
    private final String affiliateStandardTier;

    public SubscriptionPlanSeedService(
            SubscriptionPlanRepo subscriptionPlanRepo,
            @Value("${subscription.default.currency:KES}") String defaultCurrency,
            @Value("${subscription.tier.serviceprovider.standard:STANDARD}") String serviceProviderStandardTier,
            @Value("${subscription.tier.affiliate.standard:STANDARD_AFFILIATE}") String affiliateStandardTier
    ) {
        this.subscriptionPlanRepo = subscriptionPlanRepo;
        this.defaultCurrency = defaultCurrency;
        this.serviceProviderStandardTier = serviceProviderStandardTier;
        this.affiliateStandardTier = affiliateStandardTier;
    }

    @PostConstruct
    public void seedDefaults() {
        List<SubscriptionPlan> defaults = List.of(
                buildPlan(serviceProviderStandardTier, "Standard", PlanCategory.SERVICE_PROVIDER, PMSRole.SERVICE_PROVIDER),
                buildPlan(affiliateStandardTier, "Standard Affiliate", PlanCategory.AFFILIATE, PMSRole.AFFILIATE)
        );
        long createdCount = defaults.stream()
                .filter(plan -> !subscriptionPlanRepo.existsByCode(plan.getCode()))
                .map(subscriptionPlanRepo::save)
                .count();
        log.info("Subscription plan seed completed, created {} default plans.", createdCount);
    }

    private SubscriptionPlan buildPlan(String code, String displayName, PlanCategory category, PMSRole role) {
        SubscriptionPlan subscriptionPlan = SubscriptionPlan.builder()
                .code(code.trim().toUpperCase(Locale.ROOT))
                .displayName(displayName)
                .planCategory(category)
                .roleFamily(role)
                .billingCycle(BillingCycle.MONTHLY)
                .price(BigDecimal.ZERO)
                .currency(defaultCurrency.trim().toUpperCase(Locale.ROOT))
                .build();
        subscriptionPlan.setCreatedBy(0L);
        subscriptionPlan.setActive(true);
        return subscriptionPlan;
    }
}
