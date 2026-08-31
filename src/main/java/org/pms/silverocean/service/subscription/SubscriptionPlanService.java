package org.pms.silverocean.service.subscription;

import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.PlanFeatureRepo;
import org.pms.silverocean.database.pms.PlanQuotaRepo;
import org.pms.silverocean.database.pms.SubscriptionPlanRepo;
import org.pms.silverocean.database.pms.entities.PlanFeature;
import org.pms.silverocean.database.pms.entities.PlanQuota;
import org.pms.silverocean.database.pms.entities.SubscriptionPlan;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.subscription.enums.PlanCategory;
import org.pms.silverocean.service.subscription.enums.SubscriptionProduct;
import org.pms.silverocean.service.subscription.enums.SubscriptionPurchaseMode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class SubscriptionPlanService {
    private final SubscriptionPlanRepo subscriptionPlanRepo;
    private final PlanFeatureRepo planFeatureRepo;
    private final PlanQuotaRepo planQuotaRepo;
    private final UserDao userDao;

    public SubscriptionPlanService(
            SubscriptionPlanRepo subscriptionPlanRepo,
            PlanFeatureRepo planFeatureRepo,
            PlanQuotaRepo planQuotaRepo,
            UserDao userDao
    ) {
        this.subscriptionPlanRepo = subscriptionPlanRepo;
        this.planFeatureRepo = planFeatureRepo;
        this.planQuotaRepo = planQuotaRepo;
        this.userDao = userDao;
    }

    @Transactional
    public SubscriptionPlanResponseDTO createPlan(SubscriptionPlanRequestDTO request) {
        String normalizedCode = normalizeCode(request.code());
        if (subscriptionPlanRepo.existsByCode(normalizedCode)) {
            throw new PMSCustomException(ResponseCode.SUBSCRIPTION_PLAN_ALREADY_EXISTS);
        }
        ensureUniqueActivePackage(request.roleFamily(), request.billingCycle(), request.displayName(), null);
        SubscriptionPlan subscriptionPlan = SubscriptionPlan.builder()
                .code(normalizedCode)
                .displayName(request.displayName().trim())
                .planCategory(request.planCategory())
                .roleFamily(request.roleFamily())
                .billingCycle(request.billingCycle())
                .price(request.price())
                .currency(request.currency().trim().toUpperCase(Locale.ROOT))
                .productKey(productFor(request.planCategory()))
                .purchaseMode(modeFor(normalizedCode, request.price()))
                .tierRank(rankFor(request.displayName()))
                .build();
        subscriptionPlan.setCreatedBy(userDao.getUserId());
        subscriptionPlan.setActive(true);
        SubscriptionPlan savedPlan = subscriptionPlanRepo.save(subscriptionPlan);
        replaceFeatures(savedPlan, request.features());
        replaceQuotas(savedPlan, request.quotas());
        return toResponse(savedPlan);
    }

    @Transactional
    public SubscriptionPlanResponseDTO updatePlan(String planCode, SubscriptionPlanRequestDTO request) {
        SubscriptionPlan existingPlan = getByCodeOrThrow(planCode);
        String normalizedCode = normalizeCode(request.code());
        if (subscriptionPlanRepo.existsByCodeAndIdNot(normalizedCode, existingPlan.getId())) {
            throw new PMSCustomException(ResponseCode.SUBSCRIPTION_PLAN_ALREADY_EXISTS);
        }
        if (existingPlan.isActive()) {
            ensureUniqueActivePackage(request.roleFamily(), request.billingCycle(), request.displayName(), existingPlan.getId());
        }
        existingPlan.setCode(normalizedCode);
        existingPlan.setDisplayName(request.displayName().trim());
        existingPlan.setPlanCategory(request.planCategory());
        existingPlan.setRoleFamily(request.roleFamily());
        existingPlan.setBillingCycle(request.billingCycle());
        existingPlan.setPrice(request.price());
        existingPlan.setCurrency(request.currency().trim().toUpperCase(Locale.ROOT));
        existingPlan.setProductKey(productFor(request.planCategory()));
        existingPlan.setPurchaseMode(modeFor(normalizedCode, request.price()));
        existingPlan.setTierRank(rankFor(request.displayName()));
        SubscriptionPlan savedPlan = subscriptionPlanRepo.save(existingPlan);
        replaceFeatures(savedPlan, request.features());
        replaceQuotas(savedPlan, request.quotas());
        return toResponse(savedPlan);
    }

    @Transactional(readOnly = true)
    public SubscriptionPlanResponseDTO getPlanByCode(String planCode) {
        return toResponse(getByCodeOrThrow(planCode));
    }

    @Transactional(readOnly = true)
    public Page<SubscriptionPlanResponseDTO> listPlans(Pageable pageable, PlanCategory category) {
        Specification<SubscriptionPlan> specification = Specification.where(null);
        if (category != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("planCategory"), category));
        }
        return subscriptionPlanRepo.findAll(specification, pageable).map(this::toResponse);
    }

    @Transactional
    public void updatePlanStatus(String planCode, boolean active) {
        SubscriptionPlan subscriptionPlan = getByCodeOrThrow(planCode);
        if (active && !subscriptionPlan.isActive()) {
            ensureUniqueActivePackage(subscriptionPlan.getRoleFamily(), subscriptionPlan.getBillingCycle(),
                    subscriptionPlan.getDisplayName(), subscriptionPlan.getId());
        }
        subscriptionPlan.setActive(active);
        subscriptionPlanRepo.save(subscriptionPlan);
    }

    private void ensureUniqueActivePackage(org.pms.silverocean.service.auth.roles.enums.PMSRole role,
                                           org.pms.silverocean.service.subscription.enums.BillingCycle cycle,
                                           String displayName,
                                           Long excludedId) {
        String normalizedName = displayName.trim();
        boolean duplicate = excludedId == null
                ? subscriptionPlanRepo.existsByRoleFamilyAndBillingCycleAndDisplayNameIgnoreCaseAndActiveTrue(
                        role, cycle, normalizedName)
                : subscriptionPlanRepo.existsByRoleFamilyAndBillingCycleAndDisplayNameIgnoreCaseAndActiveTrueAndIdNot(
                        role, cycle, normalizedName, excludedId);
        if (duplicate) {
            throw new PMSCustomException(ResponseCode.SUBSCRIPTION_PLAN_ALREADY_EXISTS);
        }
    }

    /**
     * Add or update plan features and/or quotas by key (does not remove keys omitted from the request).
     */
    @Transactional
    public SubscriptionPlanResponseDTO upsertPlanCatalog(String planCode, PlanCatalogUpsertDTO request) {
        boolean hasFeatures = request.features() != null && !request.features().isEmpty();
        boolean hasQuotas = request.quotas() != null && !request.quotas().isEmpty();
        if (!hasFeatures && !hasQuotas) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
        }
        SubscriptionPlan plan = getByCodeOrThrow(planCode);
        if (hasFeatures) {
            for (PlanFeatureDTO dto : request.features()) {
                upsertOneFeature(plan, dto);
            }
        }
        if (hasQuotas) {
            for (PlanQuotaDTO dto : request.quotas()) {
                upsertOneQuota(plan, dto);
            }
        }
        return toResponse(plan);
    }

    private void upsertOneFeature(SubscriptionPlan plan, PlanFeatureDTO dto) {
        String key = dto.featureKey().trim();
        Optional<PlanFeature> existing = planFeatureRepo.findTopBySubscriptionPlanAndFeatureKeyOrderByIdDesc(plan, key);
        if (existing.isPresent()) {
            PlanFeature f = existing.get();
            f.setEnabled(dto.enabled());
            f.setActive(true);
            planFeatureRepo.save(f);
        } else {
            PlanFeature feature = PlanFeature.builder()
                    .featureKey(key)
                    .enabled(dto.enabled())
                    .subscriptionPlan(plan)
                    .build();
            feature.setCreatedBy(userDao.getUserId());
            feature.setActive(true);
            planFeatureRepo.save(feature);
        }
    }

    private void upsertOneQuota(SubscriptionPlan plan, PlanQuotaDTO dto) {
        String key = dto.metricKey().trim();
        Optional<PlanQuota> existing = planQuotaRepo.findTopBySubscriptionPlanAndMetricKeyOrderByIdDesc(plan, key);
        if (existing.isPresent()) {
            PlanQuota q = existing.get();
            q.setLimitValue(dto.limitValue());
            q.setActive(true);
            planQuotaRepo.save(q);
        } else {
            PlanQuota quota = PlanQuota.builder()
                    .metricKey(key)
                    .limitValue(dto.limitValue())
                    .subscriptionPlan(plan)
                    .build();
            quota.setCreatedBy(userDao.getUserId());
            quota.setActive(true);
            planQuotaRepo.save(quota);
        }
    }

    private SubscriptionPlan getByCodeOrThrow(String planCode) {
        return subscriptionPlanRepo.findByCode(normalizeCode(planCode))
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SUBSCRIPTION_PLAN_NOT_FOUND));
    }

    private SubscriptionPlanResponseDTO toResponse(SubscriptionPlan subscriptionPlan) {
        List<PlanFeatureDTO> features = planFeatureRepo.findBySubscriptionPlanAndActiveTrue(subscriptionPlan).stream()
                .map(feature -> new PlanFeatureDTO(feature.getFeatureKey(), feature.isEnabled()))
                .toList();
        List<PlanQuotaDTO> quotas = planQuotaRepo.findBySubscriptionPlanAndActiveTrue(subscriptionPlan).stream()
                .map(quota -> new PlanQuotaDTO(quota.getMetricKey(), quota.getLimitValue()))
                .toList();
        return new SubscriptionPlanResponseDTO(
                subscriptionPlan.getUuid(),
                subscriptionPlan.getCode(),
                subscriptionPlan.getDisplayName(),
                subscriptionPlan.getPlanCategory(),
                subscriptionPlan.getRoleFamily(),
                subscriptionPlan.getBillingCycle(),
                subscriptionPlan.getPrice(),
                subscriptionPlan.getCurrency(),
                productOrDefault(subscriptionPlan),
                modeOrDefault(subscriptionPlan),
                subscriptionPlan.getTierRank() == null ? rankFor(subscriptionPlan.getDisplayName()) : subscriptionPlan.getTierRank(),
                subscriptionPlan.isActive(),
                features,
                quotas
        );
    }

    private void replaceFeatures(SubscriptionPlan plan, List<PlanFeatureDTO> features) {
        planFeatureRepo.deleteBySubscriptionPlan(plan);
        List<PlanFeatureDTO> safeFeatures = features == null ? Collections.emptyList() : features;
        List<PlanFeature> planFeatures = safeFeatures.stream().map(featureDTO -> {
            PlanFeature feature = PlanFeature.builder()
                    .featureKey(featureDTO.featureKey().trim())
                    .enabled(featureDTO.enabled())
                    .subscriptionPlan(plan)
                    .build();
            feature.setCreatedBy(userDao.getUserId());
            feature.setActive(true);
            return feature;
        }).toList();
        if (!planFeatures.isEmpty()) {
            planFeatureRepo.saveAll(planFeatures);
        }
    }

    private void replaceQuotas(SubscriptionPlan plan, List<PlanQuotaDTO> quotas) {
        planQuotaRepo.deleteBySubscriptionPlan(plan);
        List<PlanQuotaDTO> safeQuotas = quotas == null ? Collections.emptyList() : quotas;
        List<PlanQuota> planQuotas = safeQuotas.stream().map(quotaDTO -> {
            PlanQuota quota = PlanQuota.builder()
                    .metricKey(quotaDTO.metricKey().trim())
                    .limitValue(quotaDTO.limitValue())
                    .subscriptionPlan(plan)
                    .build();
            quota.setCreatedBy(userDao.getUserId());
            quota.setActive(true);
            return quota;
        }).toList();
        if (!planQuotas.isEmpty()) {
            planQuotaRepo.saveAll(planQuotas);
        }
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private SubscriptionProduct productFor(PlanCategory category) {
        return switch (category) {
            case LANDLORD -> SubscriptionProduct.LANDLORD;
            case ESTATE_MANAGEMENT -> SubscriptionProduct.ESTATE_MANAGEMENT;
            case PROPERTY_SALES -> SubscriptionProduct.PROPERTY_SALES;
            case ASSET_PORTFOLIO_MANAGER -> SubscriptionProduct.MY_WEALTH;
            case AFFILIATE -> SubscriptionProduct.AFFILIATE;
            case SERVICE_PROVIDER -> SubscriptionProduct.SERVICES;
        };
    }

    private SubscriptionProduct productOrDefault(SubscriptionPlan plan) {
        return plan.getProductKey() == null ? productFor(plan.getPlanCategory()) : plan.getProductKey();
    }

    private SubscriptionPurchaseMode modeFor(String code, java.math.BigDecimal price) {
        if (code.contains("CUSTOM")) return SubscriptionPurchaseMode.SALES_MANAGED;
        return price != null && price.signum() > 0
                ? SubscriptionPurchaseMode.SELF_SERVICE : SubscriptionPurchaseMode.FREE;
    }

    private SubscriptionPurchaseMode modeOrDefault(SubscriptionPlan plan) {
        return plan.getPurchaseMode() == null ? modeFor(plan.getCode(), plan.getPrice()) : plan.getPurchaseMode();
    }

    private int rankFor(String displayName) {
        if (displayName == null) return 0;
        return switch (displayName.trim().toUpperCase(Locale.ROOT)) {
            case "BRONZE" -> 10;
            case "SILVER" -> 20;
            case "GOLD" -> 30;
            case "PLATINUM" -> 40;
            default -> 0;
        };
    }
}
