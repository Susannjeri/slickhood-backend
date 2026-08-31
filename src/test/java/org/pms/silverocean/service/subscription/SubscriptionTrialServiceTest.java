package org.pms.silverocean.service.subscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.RoleRepo;
import org.pms.silverocean.database.pms.SubscriptionEventRepo;
import org.pms.silverocean.database.pms.SubscriptionPlanRepo;
import org.pms.silverocean.database.pms.UserRoleRepo;
import org.pms.silverocean.database.pms.UserSubscriptionRepo;
import org.pms.silverocean.database.pms.entities.Role;
import org.pms.silverocean.database.pms.entities.SubscriptionEvent;
import org.pms.silverocean.database.pms.entities.SubscriptionPlan;
import org.pms.silverocean.database.pms.entities.UserSubscription;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.subscription.enums.BillingCycle;
import org.pms.silverocean.service.subscription.enums.PlanCategory;
import org.pms.silverocean.service.subscription.enums.SubscriptionEventType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionTrialServiceTest {
    @Mock UserDao userDao;
    @Mock RoleRepo roleRepo;
    @Mock UserRoleRepo userRoleRepo;
    @Mock SubscriptionPlanRepo planRepo;
    @Mock UserSubscriptionRepo subscriptionRepo;
    @Mock SubscriptionEventRepo eventRepo;
    @Mock DefaultFreePlanCodeResolver planCodeResolver;
    @Mock SubscriptionPlanService planService;
    @Mock SubscriptionInvoiceService invoiceService;

    private SubscriptionProvisioningService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionProvisioningService(userDao, roleRepo, userRoleRepo, planRepo,
                subscriptionRepo, eventRepo, planCodeResolver, planService, invoiceService, 30, 21);
    }

    @Test
    void startsConfiguredTrialForSelectedRoleWithoutTouchingOtherRoles() {
        long userId = 7L;
        Role role = Role.builder().name(PMSRole.LANDLORD.getName()).description("owner").selfAssignable(true).build();
        role.setId(2L);
        role.setActive(true);
        SubscriptionPlan plan = plan("LANDLORD_SILVER", "Silver", PMSRole.LANDLORD);

        when(userDao.getUserId()).thenReturn(userId);
        when(subscriptionRepo.findTopByCreatedByAndRoleOrderByStartAtDesc(userId, PMSRole.LANDLORD))
                .thenReturn(Optional.empty());
        when(planCodeResolver.isProvisioningRole(PMSRole.LANDLORD)).thenReturn(true);
        when(roleRepo.findByName(PMSRole.LANDLORD.getName())).thenReturn(Optional.of(role));
        when(userRoleRepo.findByUserIdAndRoleId(userId, 2L)).thenReturn(1);
        when(planRepo.findByCode("LANDLORD_SILVER")).thenReturn(Optional.of(plan));
        when(subscriptionRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(planService.getPlanByCode("LANDLORD_SILVER")).thenReturn(response(plan));

        var result = service.startTrialForSessionUser("LANDLORD", "LANDLORD_SILVER");

        assertEquals(21, service.getTrialDays());
        assertEquals(21, java.time.temporal.ChronoUnit.DAYS.between(result.startAt(), result.endAt()));
        ArgumentCaptor<SubscriptionEvent> event = ArgumentCaptor.forClass(SubscriptionEvent.class);
        verify(eventRepo).save(event.capture());
        assertEquals(SubscriptionEventType.TRIAL_STARTED, event.getValue().getEventType());
        assertEquals("trial_days:21", event.getValue().getNotes());
    }

    @Test
    void preventsRepeatingTrialOnlyForTheSameRole() {
        when(userDao.getUserId()).thenReturn(7L);
        when(subscriptionRepo.findTopByCreatedByAndRoleOrderByStartAtDesc(7L, PMSRole.LANDLORD))
                .thenReturn(Optional.of(new UserSubscription()));

        PMSCustomException error = assertThrows(PMSCustomException.class,
                () -> service.startTrialForSessionUser("LANDLORD", "LANDLORD_BRONZE"));

        assertEquals(ResponseCode.SUBSCRIPTION_TRIAL_ALREADY_USED, error.getResponseCode());
        verify(subscriptionRepo).findTopByCreatedByAndRoleOrderByStartAtDesc(7L, PMSRole.LANDLORD);
    }

    @Test
    void salesManagedPlanCannotBeActivatedByPostingZeroPriceDirectly() {
        Role role = Role.builder().name(PMSRole.LANDLORD.getName()).description("owner").selfAssignable(true).build();
        role.setId(2L); role.setActive(true);
        SubscriptionPlan plan = plan("LANDLORD_PLATINUM_CUSTOM", "Platinum", PMSRole.LANDLORD);
        plan.setPrice(BigDecimal.ZERO);
        plan.setPurchaseMode(org.pms.silverocean.service.subscription.enums.SubscriptionPurchaseMode.SALES_MANAGED);
        when(userDao.getUserId()).thenReturn(7L);
        when(planCodeResolver.isProvisioningRole(PMSRole.LANDLORD)).thenReturn(true);
        when(roleRepo.findByName(PMSRole.LANDLORD.getName())).thenReturn(Optional.of(role));
        when(userRoleRepo.findByUserIdAndRoleId(7L, 2L)).thenReturn(1);
        when(planRepo.findByCode("LANDLORD_PLATINUM_CUSTOM")).thenReturn(Optional.of(plan));

        PMSCustomException error = assertThrows(PMSCustomException.class,
                () -> service.subscribeOrUpgradeForSessionUser("LANDLORD", "LANDLORD_PLATINUM_CUSTOM", null));

        assertEquals(ResponseCode.SUBSCRIPTION_SALES_MANAGED_REQUIRED, error.getResponseCode());
    }

    private SubscriptionPlan plan(String code, String name, PMSRole role) {
        SubscriptionPlan plan = SubscriptionPlan.builder().code(code).displayName(name)
                .planCategory(PlanCategory.LANDLORD).roleFamily(role).billingCycle(BillingCycle.MONTHLY)
                .price(new BigDecimal("3500")).currency("KES")
                .productKey(org.pms.silverocean.service.subscription.enums.SubscriptionProduct.LANDLORD)
                .purchaseMode(org.pms.silverocean.service.subscription.enums.SubscriptionPurchaseMode.SELF_SERVICE)
                .tierRank(10).build();
        plan.setActive(true);
        return plan;
    }

    private SubscriptionPlanResponseDTO response(SubscriptionPlan plan) {
        return new SubscriptionPlanResponseDTO(UUID.randomUUID(), plan.getCode(), plan.getDisplayName(),
                plan.getPlanCategory(), plan.getRoleFamily(), plan.getBillingCycle(), plan.getPrice(),
                plan.getCurrency(), plan.getProductKey(), plan.getPurchaseMode(), plan.getTierRank(),
                true, List.of(), List.of());
    }
}
