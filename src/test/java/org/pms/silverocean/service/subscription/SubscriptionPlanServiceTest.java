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

    private SubscriptionPlan plan(String code, String name, boolean active) {
        SubscriptionPlan plan = SubscriptionPlan.builder().code(code).displayName(name)
                .planCategory(PlanCategory.LANDLORD).roleFamily(PMSRole.LANDLORD)
                .billingCycle(BillingCycle.MONTHLY).price(new BigDecimal("1000")).currency("KES").build();
        plan.setActive(active);
        return plan;
    }
}
