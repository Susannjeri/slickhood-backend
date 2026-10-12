package org.pms.silverocean.service.sp;

import org.pms.silverocean.database.pms.ProviderServiceRepo;
import org.pms.silverocean.database.pms.entities.ProviderService;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.sp.wrappers.ProviderServiceDTO;
import org.pms.silverocean.service.payment.PaymentPlatformFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import java.math.BigDecimal;

@Service
public class ProviderServiceDao {
    private static final java.util.Set<org.pms.silverocean.service.sp.enums.PricingUnit> BOOKABLE_PRICING_UNITS =
            java.util.Set.of(org.pms.silverocean.service.sp.enums.PricingUnit.PER_HOUR,
                    org.pms.silverocean.service.sp.enums.PricingUnit.PER_DAY,
                    org.pms.silverocean.service.sp.enums.PricingUnit.PER_WEEK,
                    org.pms.silverocean.service.sp.enums.PricingUnit.PER_MONTH,
                    org.pms.silverocean.service.sp.enums.PricingUnit.PER_JOB,
                    org.pms.silverocean.service.sp.enums.PricingUnit.FIXED);
    private final ProviderServiceRepo repo;
    private final AuditLogService auditLogService;
    private final org.pms.silverocean.database.pms.ServiceTierRepo tiers;
    private final PaymentPlatformFactory paymentPlatformFactory;

    public ProviderServiceDao(ProviderServiceRepo repo, AuditLogService auditLogService,
                              org.pms.silverocean.database.pms.ServiceTierRepo tiers,
                              PaymentPlatformFactory paymentPlatformFactory) {
        this.repo = repo;
        this.auditLogService = auditLogService;
        this.tiers = tiers;
        this.paymentPlatformFactory = paymentPlatformFactory;
    }

    public void save(ProviderService service, String auditAction) {
        service.setLastModifiedDate(LocalDateTime.now());
        repo.save(service);
        auditLogService.createAuditLog(service, auditAction);
    }

    public Page<ProviderServiceDTO> findByProfileIdEnriched(long profileId, Pageable pageable) {
        return repo.findByProfileIdEnriched(profileId, pageable);
    }

    public Page<ProviderServiceDTO> findPendingAdminReviewEnriched(Pageable pageable) {
        return repo.findPendingAdminReviewEnriched(pageable);
    }

    public Page<ProviderServiceDTO> findListedByCategoryEnriched(Pageable pageable, Long categoryId) {
        var availableChannels = paymentPlatformFactory.getAvailableChannelsForNewPayments();
        if (availableChannels.isEmpty()) return Page.empty(pageable);
        return repo.findListedByCategoryEnriched(pageable, categoryId,
                BOOKABLE_PRICING_UNITS,
                org.pms.silverocean.service.subscription.enums.SubscriptionProduct.SERVICES,
                org.pms.silverocean.service.subscription.enums.SubscriptionStatus.ACTIVE,
                org.pms.silverocean.service.kyc.AccountStatus.ACTIVE.name(),
                org.pms.silverocean.service.account.enums.AccountCategory.MERCHANT,
                availableChannels);
    }

    public Page<ProviderServiceDTO> searchDirectory(Pageable pageable, Long categoryId, String query,
                                                     BigDecimal minAmount, BigDecimal maxAmount,
                                                     org.pms.silverocean.service.sp.enums.PricingUnit pricingUnit,
                                                     Double latitude, Double longitude, Double minLat, Double maxLat, Double minLng, Double maxLng) {
        var availableChannels = paymentPlatformFactory.getAvailableChannelsForNewPayments();
        if (availableChannels.isEmpty()) return Page.empty(pageable);
        java.util.Set<org.pms.silverocean.service.sp.enums.PricingUnit> filterPricingUnits = pricingUnit == null
                ? BOOKABLE_PRICING_UNITS
                : pricingUnit == org.pms.silverocean.service.sp.enums.PricingUnit.PER_JOB
                    ? java.util.Set.of(org.pms.silverocean.service.sp.enums.PricingUnit.PER_JOB,
                            org.pms.silverocean.service.sp.enums.PricingUnit.FIXED)
                    : java.util.Set.of(pricingUnit);
        return repo.searchDirectory(pageable, categoryId, query, minAmount, maxAmount, filterPricingUnits, latitude, longitude,
                minLat, maxLat, minLng, maxLng,
                BOOKABLE_PRICING_UNITS,
                org.pms.silverocean.service.subscription.enums.SubscriptionProduct.SERVICES,
                org.pms.silverocean.service.subscription.enums.SubscriptionStatus.ACTIVE,
                org.pms.silverocean.service.kyc.AccountStatus.ACTIVE.name(),
                org.pms.silverocean.service.account.enums.AccountCategory.MERCHANT,
                availableChannels);
    }

    public Page<ProviderServiceDTO> searchDirectory(Pageable pageable, Long categoryId, String query,
                                                     BigDecimal minAmount, BigDecimal maxAmount) {
        return searchDirectory(pageable,categoryId,query,minAmount,maxAmount,null,null,null,null,null,null,null);
    }

    public Page<ProviderServiceDTO> searchDirectory(Pageable pageable, Long categoryId, String query,
                                                     BigDecimal minAmount, BigDecimal maxAmount,
                                                     Double latitude, Double longitude, Double minLat,
                                                     Double maxLat, Double minLng, Double maxLng) {
        return searchDirectory(pageable, categoryId, query, minAmount, maxAmount, null,
                latitude, longitude, minLat, maxLat, minLng, maxLng);
    }

    public Optional<ProviderServiceDTO> findByIdEnriched(long id) {
        var availableChannels = paymentPlatformFactory.getAvailableChannelsForNewPayments();
        if (availableChannels.isEmpty()) return Optional.empty();
        return repo.findByIdEnriched(id,
                BOOKABLE_PRICING_UNITS,
                org.pms.silverocean.service.subscription.enums.SubscriptionProduct.SERVICES,
                org.pms.silverocean.service.subscription.enums.SubscriptionStatus.ACTIVE,
                org.pms.silverocean.service.kyc.AccountStatus.ACTIVE.name(),
                org.pms.silverocean.service.account.enums.AccountCategory.MERCHANT,
                availableChannels);
    }

    public Optional<ProviderService> findById(long id) {
        return repo.findById(id);
    }

    public String requireActiveTier(String name) {
        return tiers.findByNameIgnoreCaseAndActiveTrue(name.trim()).map(org.pms.silverocean.database.pms.entities.ServiceTier::getName)
                .orElseThrow(() -> new org.pms.silverocean.service.PMSCustomException(org.pms.silverocean.common.ResponseCode.INVALID_FIELD_DATA));
    }
    public Optional<ProviderService> findByIdForUpdate(long id){return repo.findByIdForUpdate(id);}
    public Optional<ProviderService> findAnyByIdForUpdate(long id){return repo.findAnyByIdForUpdate(id);}
    public Optional<ProviderService> findOwnedForUpdate(long id,long profileId){return repo.findOwnedForUpdate(id,profileId);}

    public List<ProviderService> findAllById(Iterable<Long> ids) { return repo.findAllById(ids); }

    public Optional<ProviderService> findByIdAndProfileId(long id, long profileId) {
        return repo.findByIdAndProfileId(id, profileId);
    }
}
