package org.pms.silverocean.service.subscription;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.database.pms.PlanFeatureRepo;
import org.pms.silverocean.database.pms.PlanQuotaRepo;
import org.pms.silverocean.database.pms.SubscriptionPlanRepo;
import org.pms.silverocean.database.pms.entities.PlanFeature;
import org.pms.silverocean.database.pms.entities.PlanQuota;
import org.pms.silverocean.database.pms.entities.SubscriptionPlan;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.subscription.enums.BillingCycle;
import org.pms.silverocean.service.subscription.enums.PlanCategory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
@Slf4j
public class LandlordPlanCatalogSeedService {
    private static final List<String> SHARED_FEATURES = List.of(
            "LANDLORD_PAYMENT_SETUP", "TENANT_ONBOARDING", "BRANDED_INVOICES",
            "MPESA_PAYMENTS", "CARD_PAYMENTS", "PER_PROPERTY_PAYMENT_ACCOUNT",
            "AUTOMATED_RENT_REMINDERS", "LATE_FEE_RULES", "RECEIPTS_AND_STATEMENTS",
            "ANALYTICS_AND_REPORTS", "MULTIPLE_USER_ROLES", "CUSTOM_PAYMENT_SPLIT_RULES",
            "PRIORITY_SUPPORT", "DEDICATED_ONBOARDING", "WHITE_LABEL_BRANDING",
            "SLA_UPTIME_GUARANTEE", "GATE_MANAGEMENT_INCLUDED_UNITS",
            "UNIT_LISTING_INCLUDED_UNITS", "WEALTH_INCLUDED_UNITS",
            "TENANT_SERVICE_PROVIDER_ACCESS", "CUSTOM_INTEGRATIONS_AT_COST",
            "PHONE_AND_CHAT_SUPPORT_24_7", "DEDICATED_ACCOUNT_MANAGER"
    );

    private final SubscriptionPlanRepo planRepo;
    private final PlanFeatureRepo featureRepo;
    private final PlanQuotaRepo quotaRepo;
    private final String currency;
    private final int trialDays;

    public LandlordPlanCatalogSeedService(
            SubscriptionPlanRepo planRepo,
            PlanFeatureRepo featureRepo,
            PlanQuotaRepo quotaRepo,
            @Value("${subscription.default.currency:KES}") String currency,
            @Value("${subscription.trial.days:14}") int trialDays) {
        this.planRepo = planRepo;
        this.featureRepo = featureRepo;
        this.quotaRepo = quotaRepo;
        this.currency = currency.trim().toUpperCase();
        this.trialDays = Math.max(1, trialDays);
    }

    @PostConstruct
    @Transactional
    public void seed() {
        seedFamily("LANDLORD", PlanCategory.LANDLORD, PMSRole.LANDLORD);
        seedFamily("ESTATE", PlanCategory.ESTATE_MANAGEMENT, PMSRole.ESTATE_MANAGER);
        seedFamily("SALE", PlanCategory.PROPERTY_SALES, PMSRole.SALES_AGENT);
        seedFamily("WEALTH", PlanCategory.ASSET_PORTFOLIO_MANAGER, PMSRole.ASSET_PORTFOLIO_MANAGER);
        deactivateLegacyStarterIfUntouched();
        log.info("Rental, estate, property-sale and Wealth Bronze/Silver/Gold/Platinum catalogs are available; existing admin edits were preserved.");
    }

    private void seedFamily(String prefix, PlanCategory category, PMSRole role) {
        String areaFeature = switch (category) {
            case LANDLORD -> "PROPERTY_RENTALS";
            case ESTATE_MANAGEMENT -> "ESTATE_MANAGEMENT";
            case PROPERTY_SALES -> "PROPERTY_SALES";
            case ASSET_PORTFOLIO_MANAGER -> "WEALTH_MANAGEMENT";
            default -> throw new IllegalArgumentException("Unsupported canonical package category: " + category);
        };
        createIfMissing(prefix + "_BRONZE", "Bronze", category, role, BillingCycle.MONTHLY, "1000", 10L, false, areaFeature);
        createIfMissing(prefix + "_SILVER", "Silver", category, role, BillingCycle.MONTHLY, "3500", 50L, false, areaFeature);
        createIfMissing(prefix + "_GOLD", "Gold", category, role, BillingCycle.MONTHLY, "7000", 100L, false, areaFeature);
        createIfMissing(prefix + "_PLATINUM_CUSTOM", "Platinum", category, role, BillingCycle.MONTHLY, "0", -1L, true, areaFeature);
        createIfMissing(prefix + "_BRONZE_ANNUAL", "Bronze", category, role, BillingCycle.YEARLY, "10800", 10L, false, areaFeature);
        createIfMissing(prefix + "_SILVER_ANNUAL", "Silver", category, role, BillingCycle.YEARLY, "37800", 50L, false, areaFeature);
        createIfMissing(prefix + "_GOLD_ANNUAL", "Gold", category, role, BillingCycle.YEARLY, "75600", 100L, false, areaFeature);
        createIfMissing(prefix + "_PLATINUM_ANNUAL_CUSTOM", "Platinum", category, role, BillingCycle.YEARLY, "0", -1L, true, areaFeature);
        if (role == PMSRole.LANDLORD || role == PMSRole.ESTATE_MANAGER || role == PMSRole.SALES_AGENT) {
            ensureQuotaIfMissing(prefix + "_BRONZE", "TEAM_SEATS", 2L);
            ensureQuotaIfMissing(prefix + "_SILVER", "TEAM_SEATS", 5L);
            ensureQuotaIfMissing(prefix + "_GOLD", "TEAM_SEATS", 15L);
            ensureQuotaIfMissing(prefix + "_PLATINUM_CUSTOM", "TEAM_SEATS", -1L);
            ensureQuotaIfMissing(prefix + "_BRONZE_ANNUAL", "TEAM_SEATS", 2L);
            ensureQuotaIfMissing(prefix + "_SILVER_ANNUAL", "TEAM_SEATS", 5L);
            ensureQuotaIfMissing(prefix + "_GOLD_ANNUAL", "TEAM_SEATS", 15L);
            ensureQuotaIfMissing(prefix + "_PLATINUM_ANNUAL_CUSTOM", "TEAM_SEATS", -1L);
        }
    }

    private void createIfMissing(String code, String name, PlanCategory category, PMSRole role, BillingCycle cycle, String price,
                                 long unitLimit, boolean customPricing, String areaFeature) {
        if (planRepo.existsByCode(code)) {
            return;
        }
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setCode(code);
        plan.setDisplayName(name);
        plan.setPlanCategory(category);
        plan.setRoleFamily(role);
        plan.setBillingCycle(cycle);
        plan.setPrice(new BigDecimal(price));
        plan.setCurrency(currency);
        plan.setCreatedBy(0L);
        plan.setActive(true);
        plan = planRepo.save(plan);

        for (String featureKey : SHARED_FEATURES) {
            upsertFeature(plan, featureKey);
        }
        upsertFeature(plan, areaFeature);
        if (customPricing) {
            upsertFeature(plan, "API_ACCESS");
            upsertFeature(plan, "CUSTOM_PRICING");
        }
        upsertQuota(plan, "UNITS", unitLimit);
        upsertQuota(plan, "TRIAL_DAYS", trialDays);
        upsertQuota(plan, "ANNUAL_SAVING_PERCENT", customPricing ? -1L : 10L);
    }

    private void deactivateLegacyStarterIfUntouched() {
        planRepo.findByCode("STARTER")
                .filter(plan -> "Starter".equalsIgnoreCase(plan.getDisplayName()))
                .filter(plan -> plan.getPrice() == null || plan.getPrice().compareTo(BigDecimal.ZERO) == 0)
                .ifPresent(plan -> {
                    plan.setActive(false);
                    planRepo.save(plan);
                });
    }

    private void upsertFeature(SubscriptionPlan plan, String key) {
        PlanFeature feature = featureRepo
                .findTopBySubscriptionPlanAndFeatureKeyOrderByIdDesc(plan, key)
                .orElseGet(PlanFeature::new);
        feature.setFeatureKey(key);
        feature.setEnabled(true);
        feature.setSubscriptionPlan(plan);
        feature.setCreatedBy(0L);
        feature.setActive(true);
        featureRepo.save(feature);
    }

    private void upsertQuota(SubscriptionPlan plan, String key, long value) {
        PlanQuota quota = quotaRepo
                .findTopBySubscriptionPlanAndMetricKeyOrderByIdDesc(plan, key)
                .orElseGet(PlanQuota::new);
        quota.setMetricKey(key);
        quota.setLimitValue(value);
        quota.setSubscriptionPlan(plan);
        quota.setCreatedBy(0L);
        quota.setActive(true);
        quotaRepo.save(quota);
    }

    private void ensureQuotaIfMissing(String planCode, String key, long value) {
        planRepo.findByCode(planCode).ifPresent(plan -> {
            if (quotaRepo.findTopBySubscriptionPlanAndMetricKeyOrderByIdDesc(plan, key).isEmpty()) {
                upsertQuota(plan, key, value);
            }
        });
    }
}
