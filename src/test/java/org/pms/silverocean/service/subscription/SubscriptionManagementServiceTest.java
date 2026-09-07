package org.pms.silverocean.service.subscription;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.controller.wrappers.SubscriptionCurrentDTO;
import org.pms.silverocean.controller.wrappers.SubscriptionPlanSummaryRestDTO;
import org.pms.silverocean.database.pms.PlanFeatureRepo;
import org.pms.silverocean.database.pms.PMSInvoiceRepo;
import org.pms.silverocean.database.pms.SubscriptionEventRepo;
import org.pms.silverocean.database.pms.SubscriptionPlanRepo;
import org.pms.silverocean.database.pms.UserSubscriptionRepo;
import org.pms.silverocean.database.pms.entities.PlanFeature;
import org.pms.silverocean.database.pms.entities.SubscriptionPlan;
import org.pms.silverocean.database.pms.entities.UserSubscription;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.property.UnitReportDao;
import org.pms.silverocean.service.subscription.enums.SubscriptionProduct;
import org.pms.silverocean.service.subscription.enums.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubscriptionManagementServiceTest {
    @Test
    void overviewCombinesEnabledPrimaryAndLiveAddOnFeatures() {
        UserDao users = mock(UserDao.class);
        UserSubscriptionRepo subscriptions = mock(UserSubscriptionRepo.class);
        SubscriptionPlanRepo plans = mock(SubscriptionPlanRepo.class);
        PlanFeatureRepo features = mock(PlanFeatureRepo.class);
        SubscriptionEventRepo events = mock(SubscriptionEventRepo.class);
        SubscriptionProvisioningService provisioning = mock(SubscriptionProvisioningService.class);
        UnitReportDao usage = mock(UnitReportDao.class);
        SubscriptionManagementService service = new SubscriptionManagementService(users, subscriptions, plans,
                features, events, mock(PMSInvoiceRepo.class), usage, provisioning,
                mock(SubscriptionInvoiceService.class), mock(NotificationService.class));

        when(users.getUserId()).thenReturn(7L);
        var summary = new SubscriptionPlanSummaryRestDTO(null, "LANDLORD_BRONZE", "Bronze", "STANDARD",
                PMSRole.LANDLORD.name(), "MONTHLY", BigDecimal.TEN, "KES", SubscriptionProduct.LANDLORD.name(),
                "SELF_SERVICE", 1, true,
                List.of(new PlanFeatureDTO("property_management", true), new PlanFeatureDTO("premium_reports", false)),
                List.of());
        var current = new SubscriptionCurrentDTO(null, PMSRole.LANDLORD.name(), "LANDLORD_BRONZE",
                SubscriptionProduct.LANDLORD.name(), 1, SubscriptionStatus.ACTIVE, ZonedDateTime.now(), null,
                true, summary);
        when(provisioning.getCurrentSubscriptionForSessionProduct(SubscriptionProduct.LANDLORD.name()))
                .thenReturn(current);

        UserSubscription primary = subscription(SubscriptionProduct.LANDLORD, "LANDLORD_BRONZE", null);
        UserSubscription addOn = subscription(SubscriptionProduct.LISTING_ADDON, "LISTING_ADDON", null);
        UserSubscription expired = subscription(SubscriptionProduct.GATE_MANAGEMENT_ADDON, "GATE_ADDON",
                ZonedDateTime.now().minusMinutes(1));
        when(subscriptions.findTopByCreatedByAndProductKeyOrderByStartAtDesc(7L, SubscriptionProduct.LANDLORD))
                .thenReturn(Optional.of(primary));
        when(subscriptions.findAllByCreatedByAndStatusAndActiveTrue(7L, SubscriptionStatus.ACTIVE))
                .thenReturn(List.of(primary, addOn, expired));

        SubscriptionPlan listingPlan = new SubscriptionPlan();
        listingPlan.setCode("LISTING_ADDON");
        listingPlan.setActive(true);
        when(plans.findByCode("LISTING_ADDON")).thenReturn(Optional.of(listingPlan));
        PlanFeature enabled = feature("listing_management", true);
        PlanFeature disabled = feature("unpaid_feature", false);
        when(features.findBySubscriptionPlanAndActiveTrue(listingPlan)).thenReturn(List.of(enabled, disabled));

        var overview = service.overview(PMSRole.LANDLORD.name(), SubscriptionProduct.LANDLORD.name());

        assertThat(overview.effectiveFeatures()).containsExactly("property_management", "listing_management");
        assertThat(overview.activeAddOns()).singleElement().satisfies(addOnResult -> {
            assertThat(addOnResult.productKey()).isEqualTo(SubscriptionProduct.LISTING_ADDON.name());
            assertThat(addOnResult.features()).containsExactly("listing_management");
        });
    }

    @Test
    void overviewStillReturnsPurchasedAddOnEntitlementsWhenPrimaryPackageIsMissing() {
        UserDao users = mock(UserDao.class);
        UserSubscriptionRepo subscriptions = mock(UserSubscriptionRepo.class);
        SubscriptionPlanRepo plans = mock(SubscriptionPlanRepo.class);
        PlanFeatureRepo features = mock(PlanFeatureRepo.class);
        SubscriptionManagementService service = new SubscriptionManagementService(users, subscriptions, plans,
                features, mock(SubscriptionEventRepo.class), mock(PMSInvoiceRepo.class), mock(UnitReportDao.class),
                mock(SubscriptionProvisioningService.class), mock(SubscriptionInvoiceService.class),
                mock(NotificationService.class));

        when(users.getUserId()).thenReturn(9L);
        UserSubscription addOn = subscription(SubscriptionProduct.PORTFOLIO_MANAGEMENT_ADDON,
                "PORTFOLIO_ADDON", null);
        when(subscriptions.findAllByCreatedByAndStatusAndActiveTrue(9L, SubscriptionStatus.ACTIVE))
                .thenReturn(List.of(addOn));
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setCode("PORTFOLIO_ADDON");
        plan.setActive(false); // Retired from new sales, but the purchased term still grants access.
        when(plans.findByCode("PORTFOLIO_ADDON")).thenReturn(Optional.of(plan));
        when(features.findBySubscriptionPlanAndActiveTrue(plan))
                .thenReturn(List.of(feature("portfolio_management", true)));

        var overview = service.overview(PMSRole.LANDLORD.name(), SubscriptionProduct.LANDLORD.name());

        assertThat(overview.subscription()).isNull();
        assertThat(overview.effectiveFeatures()).containsExactly("portfolio_management");
        assertThat(overview.activeAddOns()).singleElement()
                .extracting(addOnResult -> addOnResult.productKey())
                .isEqualTo(SubscriptionProduct.PORTFOLIO_MANAGEMENT_ADDON.name());
    }

    private UserSubscription subscription(SubscriptionProduct product, String planCode, ZonedDateTime endAt) {
        UserSubscription subscription = new UserSubscription();
        subscription.setRole(PMSRole.LANDLORD);
        subscription.setProductKey(product);
        subscription.setPlanCode(planCode);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setEndAt(endAt);
        subscription.setActive(true);
        return subscription;
    }

    private PlanFeature feature(String key, boolean enabled) {
        PlanFeature feature = new PlanFeature();
        feature.setFeatureKey(key);
        feature.setEnabled(enabled);
        feature.setActive(true);
        return feature;
    }
}
