package org.pms.silverocean.service.subscription;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.PlanFeatureRepo;
import org.pms.silverocean.database.pms.PlanQuotaRepo;
import org.pms.silverocean.database.pms.SubscriptionPlanRepo;
import org.pms.silverocean.database.pms.entities.PlanFeature;
import org.pms.silverocean.database.pms.entities.PlanQuota;
import org.pms.silverocean.database.pms.entities.SubscriptionPlan;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LandlordPlanCatalogSeedServiceTest {
    @Mock SubscriptionPlanRepo plans;
    @Mock PlanFeatureRepo features;
    @Mock PlanQuotaRepo quotas;

    @Test void startupDoesNotOverwriteAnExistingAdminManagedCatalogue() {
        SubscriptionPlan existing = SubscriptionPlan.builder()
                .code("ADMIN_EDITED").displayName("Admin price").price(new BigDecimal("1234")).build();
        when(plans.findByCode(any())).thenReturn(Optional.of(existing));

        new LandlordPlanCatalogSeedService(plans, features, quotas, "KES", 14).seed();

        verify(plans, never()).save(any());
        verify(features, never()).save(any());
        verify(quotas, never()).save(any());
    }

    @Test void aFreshLandlordCatalogueIncludesTheSpreadsheetListingEntitlement() {
        when(plans.findByCode(any())).thenReturn(Optional.empty());
        when(plans.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(features.findTopBySubscriptionPlanAndFeatureKeyOrderByIdDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(features.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(quotas.findTopBySubscriptionPlanAndMetricKeyOrderByIdDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(quotas.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new LandlordPlanCatalogSeedService(plans, features, quotas, "KES", 14).seed();

        ArgumentCaptor<PlanFeature> saved = ArgumentCaptor.forClass(PlanFeature.class);
        verify(features, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertTrue(saved.getAllValues().stream().anyMatch(feature ->
                "PROPERTY_LISTINGS".equals(feature.getFeatureKey())
                        && "LANDLORD_BRONZE".equals(feature.getSubscriptionPlan().getCode())));
    }
}
