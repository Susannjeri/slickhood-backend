package org.pms.silverocean.service.subscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.SubscriptionPlan;
import org.pms.silverocean.database.pms.entities.PlanFeature;
import org.pms.silverocean.database.pms.entities.UserSubscription;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.subscription.enums.SubscriptionProduct;
import org.pms.silverocean.service.subscription.enums.SubscriptionStatus;
import org.pms.silverocean.service.teamaccess.WorkspaceSelectionService;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionEntitlementServiceTest {
    @Mock UserDao users;
    @Mock UserSubscriptionRepo subscriptions;
    @Mock SubscriptionPlanRepo plans;
    @Mock PlanFeatureRepo features;
    @Mock PlanQuotaRepo quotas;
    @Mock WorkspaceMembershipRepo memberships;
    @Mock CustomerWorkspaceRepo workspaces;
    @Mock WorkspaceSelectionService workspaceSelection;
    SubscriptionEntitlementService service;

    @BeforeEach void setup() {
        service = new SubscriptionEntitlementService(users, subscriptions, plans, features, quotas, memberships, workspaces, workspaceSelection);
        when(users.getUserId()).thenReturn(7L);
    }

    @Test void expiredTermCannotAccessPaidProduct() {
        UserSubscription expired = subscription("LANDLORD_GOLD", SubscriptionProduct.LANDLORD,
                ZonedDateTime.now().minusMinutes(1));
        when(subscriptions.findTopByCreatedByAndProductKeyAndStatusAndActiveTrueOrderByStartAtDesc(
                7L, SubscriptionProduct.LANDLORD, SubscriptionStatus.ACTIVE)).thenReturn(Optional.of(expired));

        PMSCustomException error = assertThrows(PMSCustomException.class,
                () -> service.requireProduct(SubscriptionProduct.LANDLORD));
        assertEquals(ResponseCode.SUBSCRIPTION_ACCESS_REQUIRED, error.getResponseCode());
    }

    @Test void listingAddOnUnlocksListingWhenPrimaryPlanDoesNotBundleIt() {
        UserSubscription primary = subscription("LANDLORD_BRONZE", SubscriptionProduct.LANDLORD, null);
        UserSubscription addOn = subscription("ADDON_LISTING", SubscriptionProduct.LISTING_ADDON, null);
        SubscriptionPlan primaryPlan = SubscriptionPlan.builder().code("LANDLORD_BRONZE").build();
        primaryPlan.setActive(true);
        when(subscriptions.findTopByCreatedByAndProductKeyAndStatusAndActiveTrueOrderByStartAtDesc(
                7L, SubscriptionProduct.LANDLORD, SubscriptionStatus.ACTIVE)).thenReturn(Optional.of(primary));
        when(subscriptions.findTopByCreatedByAndProductKeyAndStatusAndActiveTrueOrderByStartAtDesc(
                7L, SubscriptionProduct.LISTING_ADDON, SubscriptionStatus.ACTIVE)).thenReturn(Optional.of(addOn));
        when(plans.findByCode("LANDLORD_BRONZE")).thenReturn(Optional.of(primaryPlan));
        when(features.findTopBySubscriptionPlanAndFeatureKeyOrderByIdDesc(primaryPlan, "PROPERTY_LISTINGS"))
                .thenReturn(Optional.empty());

        service.requireFeatureOrAddOn(SubscriptionProduct.LANDLORD, "PROPERTY_LISTINGS",
                SubscriptionProduct.LISTING_ADDON);
    }

    @Test void missingBundledFeatureAndAddOnIsDenied() {
        UserSubscription primary = subscription("LANDLORD_BRONZE", SubscriptionProduct.LANDLORD, null);
        SubscriptionPlan primaryPlan = SubscriptionPlan.builder().code("LANDLORD_BRONZE").build();
        primaryPlan.setActive(true);
        when(subscriptions.findTopByCreatedByAndProductKeyAndStatusAndActiveTrueOrderByStartAtDesc(
                7L, SubscriptionProduct.LANDLORD, SubscriptionStatus.ACTIVE)).thenReturn(Optional.of(primary));
        when(subscriptions.findTopByCreatedByAndProductKeyAndStatusAndActiveTrueOrderByStartAtDesc(
                7L, SubscriptionProduct.LISTING_ADDON, SubscriptionStatus.ACTIVE)).thenReturn(Optional.empty());
        when(plans.findByCode("LANDLORD_BRONZE")).thenReturn(Optional.of(primaryPlan));

        PMSCustomException error = assertThrows(PMSCustomException.class,
                () -> service.requireFeatureOrAddOn(SubscriptionProduct.LANDLORD, "PROPERTY_LISTINGS",
                        SubscriptionProduct.LISTING_ADDON));
        assertEquals(ResponseCode.SUBSCRIPTION_FEATURE_NOT_INCLUDED, error.getResponseCode());
    }

    @Test void businessOwnerCannotUseSharedBusinessEndpointsWithoutSubscription() {
        when(users.getActiveRole()).thenReturn(PMSRole.LANDLORD);
        when(subscriptions.findTopByCreatedByAndProductKeyAndStatusAndActiveTrueOrderByStartAtDesc(
                7L, SubscriptionProduct.LANDLORD, SubscriptionStatus.ACTIVE)).thenReturn(Optional.empty());

        PMSCustomException error = assertThrows(PMSCustomException.class,
                () -> service.requireSessionBusinessProductIfApplicable());
        assertEquals(ResponseCode.SUBSCRIPTION_ACCESS_REQUIRED, error.getResponseCode());
    }

    @Test void tenantMayUseSharedEndpointsWithoutBuyingLandlordSubscription() {
        when(users.getActiveRole()).thenReturn(PMSRole.TENANT);
        service.requireSessionBusinessProductIfApplicable();
    }

    @Test void anonymousSessionMayUsePublicBusinessCatalogues() {
        when(users.getUserId()).thenReturn(null);
        service.requireSessionBusinessProductIfApplicable();
    }

    @Test void activeLandlordPropertyRouteRequiresItsIncludedFeature() {
        when(users.getActiveRole()).thenReturn(PMSRole.LANDLORD);
        UserSubscription subscription = subscription("LANDLORD_BRONZE", SubscriptionProduct.LANDLORD, null);
        SubscriptionPlan plan = SubscriptionPlan.builder().code("LANDLORD_BRONZE").build();
        plan.setActive(true);
        PlanFeature feature = new PlanFeature();
        feature.setActive(true);
        feature.setEnabled(true);
        when(subscriptions.findTopByCreatedByAndProductKeyAndStatusAndActiveTrueOrderByStartAtDesc(
                7L, SubscriptionProduct.LANDLORD, SubscriptionStatus.ACTIVE)).thenReturn(Optional.of(subscription));
        when(plans.findByCode("LANDLORD_BRONZE")).thenReturn(Optional.of(plan));
        when(features.findTopBySubscriptionPlanAndFeatureKeyOrderByIdDesc(plan, "PROPERTY_AND_UNIT_MANAGEMENT"))
                .thenReturn(Optional.of(feature));

        service.requireSessionFeatureIfApplicable("PROPERTY_AND_UNIT_MANAGEMENT",
                "ESTATE_AND_HOMEOWNER_MANAGEMENT", "PROPERTY_SALES");
    }

    @Test void tenantSharedPropertyJourneyDoesNotRequireAnOwnersFeature() {
        when(users.getActiveRole()).thenReturn(PMSRole.TENANT);
        service.requireSessionFeatureIfApplicable("PROPERTY_AND_UNIT_MANAGEMENT",
                "ESTATE_AND_HOMEOWNER_MANAGEMENT", "PROPERTY_SALES");
    }

    private UserSubscription subscription(String code, SubscriptionProduct product, ZonedDateTime endAt) {
        UserSubscription value = UserSubscription.builder().planCode(code).productKey(product)
                .status(SubscriptionStatus.ACTIVE).startAt(ZonedDateTime.now().minusDays(1)).endAt(endAt).build();
        value.setCreatedBy(7L);
        value.setActive(true);
        return value;
    }
}
