package org.pms.silverocean.service.subscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.PlanQuotaRepo;
import org.pms.silverocean.database.pms.SubscriptionPlanRepo;
import org.pms.silverocean.database.pms.UserSubscriptionRepo;
import org.pms.silverocean.database.pms.entities.PlanQuota;
import org.pms.silverocean.database.pms.entities.SubscriptionPlan;
import org.pms.silverocean.database.pms.entities.UserSubscription;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.subscription.enums.SubscriptionProduct;
import org.pms.silverocean.service.subscription.enums.SubscriptionStatus;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SharedPropertySubscriptionServiceTest {
    @Mock UserSubscriptionRepo subscriptions;
    @Mock SubscriptionPlanRepo plans;
    @Mock PlanQuotaRepo quotas;
    SharedPropertySubscriptionService service;

    @BeforeEach void setup() { service = new SharedPropertySubscriptionService(subscriptions, plans, quotas); }

    @Test void oneLandlordSubscriptionIsRecognizedForTheSharedPropertyFamily() {
        UserSubscription landlord = subscription("LANDLORD_BRONZE", SubscriptionProduct.LANDLORD);
        for (SubscriptionProduct product : SharedPropertySubscriptionService.PRODUCTS) {
            when(subscriptions.findTopByCreatedByAndProductKeyAndStatusAndActiveTrueOrderByStartAtDesc(
                    7L, product, SubscriptionStatus.ACTIVE))
                    .thenReturn(product == SubscriptionProduct.LANDLORD ? Optional.of(landlord) : Optional.empty());
        }
        assertEquals(landlord, service.requireActive(7L));
    }

    @Test void overlappingLegacyPlansDoNotStackAndUseTheSmallestFiniteLimit() {
        UserSubscription bronze = subscription("LANDLORD_BRONZE", SubscriptionProduct.LANDLORD);
        UserSubscription gold = subscription("SALE_GOLD", SubscriptionProduct.PROPERTY_SALES);
        SubscriptionPlan bronzePlan = SubscriptionPlan.builder().code("LANDLORD_BRONZE").build();
        SubscriptionPlan goldPlan = SubscriptionPlan.builder().code("SALE_GOLD").build();
        when(subscriptions.findActiveProductsForUpdate(7L, SharedPropertySubscriptionService.PRODUCTS,
                SubscriptionStatus.ACTIVE)).thenReturn(List.of(bronze, gold));
        when(plans.findByCode("LANDLORD_BRONZE")).thenReturn(Optional.of(bronzePlan));
        when(plans.findByCode("SALE_GOLD")).thenReturn(Optional.of(goldPlan));
        when(quotas.findTopBySubscriptionPlanAndMetricKeyOrderByIdDesc(bronzePlan, "UNITS"))
                .thenReturn(Optional.of(quota(bronzePlan, 10)));
        when(quotas.findTopBySubscriptionPlanAndMetricKeyOrderByIdDesc(goldPlan, "UNITS"))
                .thenReturn(Optional.of(quota(goldPlan, 100)));

        PMSCustomException error = assertThrows(PMSCustomException.class,
                () -> service.requireAvailableUnits(7L, () -> 10L, 1));
        assertEquals(ResponseCode.SUBSCRIPTION_LIMIT_EXCEEDED, error.getResponseCode());
    }

    private UserSubscription subscription(String code, SubscriptionProduct product) {
        UserSubscription value = UserSubscription.builder().planCode(code).productKey(product)
                .status(SubscriptionStatus.ACTIVE).startAt(ZonedDateTime.now()).build();
        value.setCreatedBy(7L); value.setActive(true); return value;
    }

    private PlanQuota quota(SubscriptionPlan plan, long limit) {
        PlanQuota value = new PlanQuota(); value.setSubscriptionPlan(plan); value.setMetricKey("UNITS");
        value.setLimitValue(limit); value.setActive(true); return value;
    }
}
