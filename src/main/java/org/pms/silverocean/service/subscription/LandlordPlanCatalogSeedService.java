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
import org.pms.silverocean.service.subscription.enums.SubscriptionProduct;
import org.pms.silverocean.service.subscription.enums.SubscriptionPurchaseMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
@Slf4j
public class LandlordPlanCatalogSeedService {
    private static final List<String> COMMON_FEATURES = List.of(
            "MPESA_PAYMENTS", "CARD_PAYMENTS", "RECEIPTS_AND_STATEMENTS", "ANALYTICS_AND_REPORTS");

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
        seedPermanentFreeProducts();
        seedSalesManagedAddOns();
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
        SubscriptionProduct product = productFor(category);
        createIfMissing(prefix + "_BRONZE", "Bronze", category, role, BillingCycle.MONTHLY, "1000", 10L, 10, false, areaFeature, product);
        createIfMissing(prefix + "_SILVER", "Silver", category, role, BillingCycle.MONTHLY, "3500", 50L, 20, false, areaFeature, product);
        createIfMissing(prefix + "_GOLD", "Gold", category, role, BillingCycle.MONTHLY, "7000", 100L, 30, false, areaFeature, product);
        createIfMissing(prefix + "_PLATINUM_CUSTOM", "Platinum", category, role, BillingCycle.MONTHLY, "0", -1L, 40, true, areaFeature, product);
        createIfMissing(prefix + "_BRONZE_ANNUAL", "Bronze", category, role, BillingCycle.YEARLY, "10800", 10L, 10, false, areaFeature, product);
        createIfMissing(prefix + "_SILVER_ANNUAL", "Silver", category, role, BillingCycle.YEARLY, "37800", 50L, 20, false, areaFeature, product);
        createIfMissing(prefix + "_GOLD_ANNUAL", "Gold", category, role, BillingCycle.YEARLY, "75600", 100L, 30, false, areaFeature, product);
        createIfMissing(prefix + "_PLATINUM_ANNUAL_CUSTOM", "Platinum", category, role, BillingCycle.YEARLY, "0", -1L, 40, true, areaFeature, product);
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
                                 long unitLimit, int tierRank, boolean customPricing, String areaFeature,
                                 SubscriptionProduct product) {
        SubscriptionPlan plan = planRepo.findByCode(code).orElseGet(SubscriptionPlan::new);
        plan.setCode(code);
        plan.setDisplayName(name);
        plan.setPlanCategory(category);
        plan.setRoleFamily(role);
        plan.setBillingCycle(cycle);
        plan.setPrice(new BigDecimal(price));
        plan.setCurrency(currency);
        plan.setProductKey(product);
        plan.setPurchaseMode(customPricing ? SubscriptionPurchaseMode.SALES_MANAGED : SubscriptionPurchaseMode.SELF_SERVICE);
        plan.setTierRank(tierRank);
        plan.setCreatedBy(0L);
        plan.setActive(true);
        plan = planRepo.save(plan);

        for (String featureKey : COMMON_FEATURES) {
            upsertFeature(plan, featureKey);
        }
        upsertFeature(plan, areaFeature);
        for (String featureKey : areaFeatures(category)) upsertFeature(plan, featureKey);
        if (customPricing) {
            upsertFeature(plan, "API_ACCESS");
            upsertFeature(plan, "CUSTOM_PRICING");
        }
        upsertQuota(plan, "UNITS", unitLimit);
        upsertQuota(plan, "TRIAL_DAYS", trialDays);
        upsertQuota(plan, "ANNUAL_SAVING_PERCENT", customPricing ? -1L : 10L);
    }

    private List<String> areaFeatures(PlanCategory category) {
        return switch (category) {
            case LANDLORD -> List.of("PROPERTY_AND_UNIT_MANAGEMENT", "LEASE_MANAGEMENT", "TENANT_ONBOARDING",
                    "RENT_INVOICING", "RENT_RECONCILIATION");
            case ESTATE_MANAGEMENT -> List.of("ESTATE_AND_HOMEOWNER_MANAGEMENT", "SERVICE_CHARGE_BILLING",
                    "COMMUNITY_FUNDS", "VISITOR_MANAGEMENT", "ESTATE_OPERATIONS");
            case PROPERTY_SALES -> List.of("PROPERTY_LISTINGS", "BUYER_PIPELINE", "OFFERS_AND_DUE_DILIGENCE",
                    "SALE_MILESTONES", "SALES_REPORTING");
            case ASSET_PORTFOLIO_MANAGER -> List.of("ASSET_REGISTER", "LIABILITY_REGISTER", "NET_WORTH",
                    "WEALTH_GOALS", "WEALTH_PERFORMANCE");
            default -> List.of();
        };
    }

    private SubscriptionProduct productFor(PlanCategory category) {
        return switch (category) {
            case LANDLORD -> SubscriptionProduct.LANDLORD;
            case ESTATE_MANAGEMENT -> SubscriptionProduct.ESTATE_MANAGEMENT;
            case PROPERTY_SALES -> SubscriptionProduct.PROPERTY_SALES;
            case ASSET_PORTFOLIO_MANAGER -> SubscriptionProduct.MY_WEALTH;
            case SERVICE_PROVIDER -> SubscriptionProduct.SERVICES;
            case AFFILIATE -> SubscriptionProduct.AFFILIATE;
        };
    }

    private void seedPermanentFreeProducts() {
        createFreeProduct("SERVICES_FREE", "Services", PlanCategory.SERVICE_PROVIDER, PMSRole.SERVICE_PROVIDER,
                SubscriptionProduct.SERVICES, "SERVICE_MARKETPLACE");
        createFreeProduct("SOKO_FREE", "Soko", PlanCategory.SERVICE_PROVIDER, PMSRole.SERVICE_PROVIDER,
                SubscriptionProduct.SOKO, "SOKO_MARKETPLACE");
        createFreeProduct("AFFILIATE_FREE", "Affiliate", PlanCategory.AFFILIATE, PMSRole.AFFILIATE,
                SubscriptionProduct.AFFILIATE, "AFFILIATE_PROGRAM");
    }

    private void createFreeProduct(String code, String name, PlanCategory category, PMSRole role,
                                   SubscriptionProduct product, String feature) {
        SubscriptionPlan plan = planRepo.findByCode(code).orElseGet(SubscriptionPlan::new);
        plan.setCode(code); plan.setDisplayName(name); plan.setPlanCategory(category); plan.setRoleFamily(role);
        plan.setBillingCycle(BillingCycle.MONTHLY); plan.setPrice(BigDecimal.ZERO); plan.setCurrency(currency);
        plan.setProductKey(product); plan.setPurchaseMode(SubscriptionPurchaseMode.FREE); plan.setTierRank(0);
        plan.setCreatedBy(0L); plan.setActive(true); plan = planRepo.save(plan);
        upsertFeature(plan, feature);
    }

    private void seedSalesManagedAddOns() {
        createAddOn("ADDON_GATE_MANAGEMENT", "Gate Management add-on", SubscriptionProduct.GATE_MANAGEMENT_ADDON,
                "GATE_MANAGEMENT");
        createAddOn("ADDON_LISTING", "Listing add-on", SubscriptionProduct.LISTING_ADDON,
                "EXTERNAL_UNIT_LISTING");
        createAddOn("ADDON_PORTFOLIO_MANAGEMENT", "Portfolio Management add-on",
                SubscriptionProduct.PORTFOLIO_MANAGEMENT_ADDON, "EXTERNAL_PORTFOLIO_MANAGEMENT");
    }

    private void createAddOn(String code, String name, SubscriptionProduct product, String feature) {
        SubscriptionPlan plan = planRepo.findByCode(code).orElseGet(SubscriptionPlan::new);
        plan.setCode(code); plan.setDisplayName(name); plan.setPlanCategory(PlanCategory.LANDLORD);
        plan.setRoleFamily(PMSRole.LANDLORD); plan.setBillingCycle(BillingCycle.MONTHLY);
        plan.setPrice(BigDecimal.ZERO); plan.setCurrency(currency); plan.setProductKey(product);
        plan.setPurchaseMode(SubscriptionPurchaseMode.SALES_MANAGED); plan.setTierRank(100);
        plan.setCreatedBy(0L); plan.setActive(true); plan = planRepo.save(plan);
        upsertFeature(plan, feature); upsertFeature(plan, "AT_COST");
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
