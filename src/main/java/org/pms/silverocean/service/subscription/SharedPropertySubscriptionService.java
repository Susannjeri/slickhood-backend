package org.pms.silverocean.service.subscription;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.PlanQuotaRepo;
import org.pms.silverocean.database.pms.SubscriptionPlanRepo;
import org.pms.silverocean.database.pms.UserSubscriptionRepo;
import org.pms.silverocean.database.pms.entities.UserSubscription;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.subscription.enums.SubscriptionProduct;
import org.pms.silverocean.service.subscription.enums.SubscriptionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;

@Service
@RequiredArgsConstructor
public class SharedPropertySubscriptionService {
    public static final List<SubscriptionProduct> PRODUCTS = List.of(
            SubscriptionProduct.LANDLORD,
            SubscriptionProduct.ESTATE_MANAGEMENT,
            SubscriptionProduct.PROPERTY_SALES);

    private final UserSubscriptionRepo subscriptions;
    private final SubscriptionPlanRepo plans;
    private final PlanQuotaRepo quotas;

    public boolean supports(SubscriptionProduct product) {
        return product != null && PRODUCTS.contains(product);
    }

    @Transactional(readOnly = true)
    public UserSubscription requireActive(long ownerUserId) {
        return active(ownerUserId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SUBSCRIPTION_ACCESS_REQUIRED));
    }

    @Transactional(readOnly = true)
    public Optional<UserSubscription> active(long ownerUserId) {
        return PRODUCTS.stream()
                .map(product -> subscriptions.findTopByCreatedByAndProductKeyAndStatusAndActiveTrueOrderByStartAtDesc(
                        ownerUserId, product, SubscriptionStatus.ACTIVE))
                .flatMap(Optional::stream)
                .filter(this::unexpired)
                .max(Comparator.comparing(UserSubscription::getStartAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    @Transactional
    public void requireAvailableUnits(long ownerUserId, LongSupplier currentTotalUnits, long increment) {
        List<UserSubscription> live = subscriptions.findActiveProductsForUpdate(
                        ownerUserId, PRODUCTS, SubscriptionStatus.ACTIVE).stream()
                .filter(this::unexpired)
                .toList();
        if (live.isEmpty()) throw new PMSCustomException(ResponseCode.SUBSCRIPTION_ACCESS_REQUIRED);

        // Overlapping legacy terms never stack. Use the smallest finite allowance
        // until an overlap expires; one standalone unlimited plan stays unlimited.
        long limit = live.stream().mapToLong(this::unitLimit)
                .filter(value -> value >= 0).min().orElse(-1L);
        if (limit >= 0 && currentTotalUnits.getAsLong() + increment > limit) {
            throw new PMSCustomException(ResponseCode.SUBSCRIPTION_LIMIT_EXCEEDED);
        }
    }

    private long unitLimit(UserSubscription subscription) {
        var plan = plans.findByCode(subscription.getPlanCode())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SUBSCRIPTION_ACCESS_REQUIRED));
        return quotas.findTopBySubscriptionPlanAndMetricKeyOrderByIdDesc(plan, "UNITS")
                .filter(quota -> quota.isActive()).map(quota -> quota.getLimitValue()).orElse(0L);
    }

    private boolean unexpired(UserSubscription subscription) {
        return subscription.getEndAt() == null || subscription.getEndAt().isAfter(ZonedDateTime.now());
    }
}
