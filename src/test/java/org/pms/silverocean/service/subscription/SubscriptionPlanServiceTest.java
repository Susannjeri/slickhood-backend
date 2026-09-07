package org.pms.silverocean.service.subscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.PlanFeatureRepo;
import org.pms.silverocean.database.pms.PlanQuotaRepo;
import org.pms.silverocean.database.pms.SubscriptionPlanRepo;
import org.pms.silverocean.database.pms.entities.SubscriptionPlan;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.subscription.enums.BillingCycle;
import org.pms.silverocean.service.subscription.enums.PlanCategory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionPlanServiceTest {
    @Mock SubscriptionPlanRepo planRepo;
    @Mock PlanFeatureRepo featureRepo;
    @Mock PlanQuotaRepo quotaRepo;
    @Mock UserDao userDao;

    private SubscriptionPlanService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionPlanService(planRepo, featureRepo, quotaRepo, userDao);
    }

    @Test
    void rejectsSecondActiveTierForSameRoleBillingCycleAndName() {
        SubscriptionPlanRequestDTO request = request("LANDLORD_BRONZE_COPY", " Bronze ");
        when(planRepo.existsByCode("LANDLORD_BRONZE_COPY")).thenReturn(false);
        when(planRepo.existsByRoleFamilyAndBillingCycleAndDisplayNameIgnoreCaseAndActiveTrue(
                PMSRole.LANDLORD, BillingCycle.MONTHLY, "Bronze")).thenReturn(true);

        PMSCustomException error = assertThrows(PMSCustomException.class, () -> service.createPlan(request));

        assertEquals(ResponseCode.SUBSCRIPTION_PLAN_ALREADY_EXISTS, error.getResponseCode());
        verify(planRepo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsReactivationWhenCanonicalActiveTierAlreadyExists() {
        SubscriptionPlan inactive = plan("BRONZE_OLD", "Bronze", false);
        inactive.setId(18L);
        when(planRepo.findByCode("BRONZE_OLD")).thenReturn(Optional.of(inactive));
        when(planRepo.existsByRoleFamilyAndBillingCycleAndDisplayNameIgnoreCaseAndActiveTrueAndIdNot(
                PMSRole.LANDLORD, BillingCycle.MONTHLY, "Bronze", 18L)).thenReturn(true);

        PMSCustomException error = assertThrows(PMSCustomException.class,
                () -> service.updatePlanStatus("BRONZE_OLD", true));

        assertEquals(ResponseCode.SUBSCRIPTION_PLAN_ALREADY_EXISTS, error.getResponseCode());
        verify(planRepo, never()).save(inactive);
    }

    private SubscriptionPlanRequestDTO request(String code, String name) {
        return new SubscriptionPlanRequestDTO(code, name, PlanCategory.LANDLORD, PMSRole.LANDLORD,
                BillingCycle.MONTHLY, new BigDecimal("1000"), "KES", List.of(), List.of());
    }

    @Test
    void editingSokoDoesNotChangeItsProductToServices() {
        SubscriptionPlan soko = plan("SOKO_FREE", "Soko", true);
        soko.setId(7L); soko.setPlanCategory(PlanCategory.SERVICE_PROVIDER); soko.setRoleFamily(PMSRole.SERVICE_PROVIDER);
        soko.setPrice(BigDecimal.ZERO); soko.setProductKey(org.pms.silverocean.service.subscription.enums.SubscriptionProduct.SOKO);
        soko.setPurchaseMode(org.pms.silverocean.service.subscription.enums.SubscriptionPurchaseMode.FREE);
        when(planRepo.findByCode("SOKO_FREE")).thenReturn(Optional.of(soko));
        when(planRepo.save(soko)).thenReturn(soko);
        var result = service.updatePlan("SOKO_FREE", new SubscriptionPlanRequestDTO("SOKO_FREE", "Soko",
                PlanCategory.SERVICE_PROVIDER, PMSRole.SERVICE_PROVIDER, BillingCycle.MONTHLY, BigDecimal.ZERO, "KES", List.of(), List.of()));
        assertEquals(org.pms.silverocean.service.subscription.enums.SubscriptionProduct.SOKO, result.productKey());
    }

    @Test
    void editingZeroPricedAddonDoesNotMakeItFree() {
        SubscriptionPlan addon = plan("ADDON_GATE_MANAGEMENT", "Gate Management add-on", true);
        addon.setId(8L); addon.setPrice(BigDecimal.ZERO);
        addon.setProductKey(org.pms.silverocean.service.subscription.enums.SubscriptionProduct.GATE_MANAGEMENT_ADDON);
        addon.setPurchaseMode(org.pms.silverocean.service.subscription.enums.SubscriptionPurchaseMode.SALES_MANAGED);
        when(planRepo.findByCode(addon.getCode())).thenReturn(Optional.of(addon));
        when(planRepo.save(addon)).thenReturn(addon);
        var result = service.updatePlan(addon.getCode(), new SubscriptionPlanRequestDTO(addon.getCode(), addon.getDisplayName(),
                PlanCategory.LANDLORD, PMSRole.LANDLORD, BillingCycle.MONTHLY, BigDecimal.ZERO, "KES", List.of(), List.of()));
        assertEquals(org.pms.silverocean.service.subscription.enums.SubscriptionPurchaseMode.SALES_MANAGED, result.purchaseMode());
        assertEquals(org.pms.silverocean.service.subscription.enums.SubscriptionProduct.GATE_MANAGEMENT_ADDON, result.productKey());
    }

    @Test
    void refusesRenameOfPlanReferencedBySubscriptionHistory() {
        when(planRepo.findByCode("LANDLORD_BRONZE")).thenReturn(Optional.of(plan("LANDLORD_BRONZE", "Bronze", true)));
        assertThrows(PMSCustomException.class, () -> service.updatePlan("LANDLORD_BRONZE", request("RENAMED", "Bronze")));
        verify(planRepo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void retiredLegacyAliasCannotBeReactivated() {
        when(planRepo.findByCode("STANDARD")).thenReturn(Optional.of(plan("STANDARD", "Standard", false)));
        assertThrows(PMSCustomException.class, () -> service.updatePlanStatus("STANDARD", true));
        verify(planRepo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsMismatchedRoleAndCategory() {
        assertThrows(PMSCustomException.class, () -> service.createPlan(new SubscriptionPlanRequestDTO("BAD", "Bad",
                PlanCategory.ESTATE_MANAGEMENT, PMSRole.LANDLORD, BillingCycle.MONTHLY, BigDecimal.TEN, "KES", List.of(), List.of())));
        verify(planRepo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unlimitedQuotaIsValidButLowerNegativeValuesAreNot() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertEquals(0, validator.validate(new PlanQuotaDTO("UNITS", -1L)).size());
            assertEquals(1, validator.validate(new PlanQuotaDTO("UNITS", -2L)).size());
        }
    }

    private SubscriptionPlan plan(String code, String name, boolean active) {
        SubscriptionPlan plan = SubscriptionPlan.builder().code(code).displayName(name)
                .planCategory(PlanCategory.LANDLORD).roleFamily(PMSRole.LANDLORD)
                .billingCycle(BillingCycle.MONTHLY).price(new BigDecimal("1000")).currency("KES").build();
        plan.setActive(active);
        return plan;
    }
}
