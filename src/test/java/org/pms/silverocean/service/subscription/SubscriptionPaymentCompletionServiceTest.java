package org.pms.silverocean.service.subscription;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.RoleRepo;
import org.pms.silverocean.database.pms.SubscriptionEventRepo;
import org.pms.silverocean.database.pms.SubscriptionPaymentCompletionRepo;
import org.pms.silverocean.database.pms.SubscriptionPlanRepo;
import org.pms.silverocean.database.pms.UserRoleRepo;
import org.pms.silverocean.database.pms.UserSubscriptionRepo;
import org.pms.silverocean.service.payment.contract.PaidInvoiceReader;
import org.pms.silverocean.service.payment.contract.PaidInvoiceView;
import org.pms.silverocean.database.pms.entities.Role;
import org.pms.silverocean.database.pms.entities.SubscriptionPlan;
import org.pms.silverocean.database.pms.entities.UserSubscription;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.subscription.enums.BillingCycle;
import org.pms.silverocean.service.subscription.enums.PlanCategory;
import org.pms.silverocean.service.subscription.enums.SubscriptionStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionPaymentCompletionServiceTest {

    @Mock
    private PaidInvoiceReader paidInvoiceReader;
    @Mock
    private SubscriptionPaymentCompletionRepo completionRepo;
    @Mock
    private SubscriptionPlanRepo subscriptionPlanRepo;
    @Mock
    private UserSubscriptionRepo userSubscriptionRepo;
    @Mock
    private SubscriptionEventRepo subscriptionEventRepo;
    @Mock
    private RoleRepo roleRepo;
    @Mock
    private UserRoleRepo userRoleRepo;

    @InjectMocks
    private SubscriptionPaymentCompletionService service;

    @Test
    void paidSubscriptionWithMissingPlanFailsForOutboxRetry() {
        PaidInvoiceView invoice = new PaidInvoiceView(13L,"INV-MISSING","MISSING",true,0,7L);
        when(paidInvoiceReader.findByIdForUpdate(13L)).thenReturn(Optional.of(invoice));
        when(completionRepo.existsByInvoiceId(13L)).thenReturn(false);
        when(subscriptionPlanRepo.findByCodeAndActiveTrue("MISSING")).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class,()->service.completePaidSubscriptionAfterPayment(13L,"PAY-RETRY"));
        verify(completionRepo,never()).save(any());
    }

    @Test
    void completePaidSubscriptionAfterPayment_isIdempotentPerInvoice() {
        PaidInvoiceView invoice = new PaidInvoiceView(10L,"INV-TEST","PRO",true,0,5L);

        when(paidInvoiceReader.findByIdForUpdate(10L)).thenReturn(Optional.of(invoice));
        when(completionRepo.existsByInvoiceId(10L)).thenReturn(true);

        service.completePaidSubscriptionAfterPayment(10L, "MPESA123");

        verify(userSubscriptionRepo, never()).save(any());
        verify(completionRepo, never()).save(any());
    }

    @Test
    void completePaidSubscriptionAfterPayment_activatesWhenNotYetApplied() {
        PaidInvoiceView invoice = new PaidInvoiceView(11L,"INV-TEST2","PRO",true,0,7L);

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .code("PRO")
                .displayName("Pro")
                .planCategory(PlanCategory.LANDLORD)
                .roleFamily(PMSRole.LANDLORD)
                .billingCycle(BillingCycle.MONTHLY)
                .price(new BigDecimal("100"))
                .currency("KES")
                .productKey(org.pms.silverocean.service.subscription.enums.SubscriptionProduct.LANDLORD)
                .build();
        plan.setActive(true);

        Role dbRole = Role.builder()
                .name(PMSRole.LANDLORD.getName())
                .description("x")
                .selfAssignable(false)
                .build();
        dbRole.setId(2L);
        dbRole.setActive(true);

        when(paidInvoiceReader.findByIdForUpdate(11L)).thenReturn(Optional.of(invoice));
        when(completionRepo.existsByInvoiceId(11L)).thenReturn(false);
        when(subscriptionPlanRepo.findByCodeAndActiveTrue("PRO")).thenReturn(Optional.of(plan));
        when(roleRepo.findByName(PMSRole.LANDLORD.getName())).thenReturn(Optional.of(dbRole));
        when(userRoleRepo.findByUserIdAndRoleId(7L, 2L)).thenReturn(1);
        when(userSubscriptionRepo.findTopByCreatedByAndProductKeyAndStatusAndActiveTrueOrderByStartAtDesc(
                7L, org.pms.silverocean.service.subscription.enums.SubscriptionProduct.LANDLORD, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(userSubscriptionRepo.findAllByCreatedByAndProductKeyAndStatusAndActiveTrue(
                eq(7L), eq(org.pms.silverocean.service.subscription.enums.SubscriptionProduct.LANDLORD), eq(SubscriptionStatus.ACTIVE)))
                .thenReturn(java.util.List.of());

        service.completePaidSubscriptionAfterPayment(11L, "MPESA999");

        ArgumentCaptor<UserSubscription> subscription = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepo).save(subscription.capture());
        assertNotNull(subscription.getValue().getEndAt());
        assertEquals(subscription.getValue().getStartAt().plusMonths(1), subscription.getValue().getEndAt());
        verify(subscriptionEventRepo).save(any());
        verify(completionRepo).save(any());
    }

    @Test
    void completePaidSubscriptionAfterPayment_extendsSamePlanInsteadOfReplacingIt() {
        PaidInvoiceView invoice = new PaidInvoiceView(12L,"INV-RENEW","PRO",true,0,7L);

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .code("PRO").displayName("Pro").planCategory(PlanCategory.LANDLORD)
                .roleFamily(PMSRole.LANDLORD).billingCycle(BillingCycle.MONTHLY)
                .price(new BigDecimal("100")).currency("KES")
                .productKey(org.pms.silverocean.service.subscription.enums.SubscriptionProduct.LANDLORD).build();
        plan.setActive(true);
        Role role = Role.builder().name(PMSRole.LANDLORD.getName()).description("x").selfAssignable(false).build();
        role.setId(2L);
        role.setActive(true);
        UserSubscription existing = UserSubscription.builder()
                .role(PMSRole.LANDLORD).productKey(org.pms.silverocean.service.subscription.enums.SubscriptionProduct.LANDLORD)
                .planCode("PRO").status(SubscriptionStatus.ACTIVE)
                .startAt(ZonedDateTime.now().minusDays(5)).endAt(ZonedDateTime.now().plusDays(25)).build();
        existing.setId(21L);
        existing.setCreatedBy(7L);
        existing.setActive(true);
        ZonedDateTime previousEnd = existing.getEndAt();

        when(paidInvoiceReader.findByIdForUpdate(12L)).thenReturn(Optional.of(invoice));
        when(completionRepo.existsByInvoiceId(12L)).thenReturn(false);
        when(subscriptionPlanRepo.findByCodeAndActiveTrue("PRO")).thenReturn(Optional.of(plan));
        when(roleRepo.findByName(PMSRole.LANDLORD.getName())).thenReturn(Optional.of(role));
        when(userRoleRepo.findByUserIdAndRoleId(7L, 2L)).thenReturn(1);
        when(userSubscriptionRepo.findTopByCreatedByAndProductKeyAndStatusAndActiveTrueOrderByStartAtDesc(
                7L, org.pms.silverocean.service.subscription.enums.SubscriptionProduct.LANDLORD, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existing));

        service.completePaidSubscriptionAfterPayment(12L, "PAYSTACK-RENEW");

        assertEquals(previousEnd.plusMonths(1), existing.getEndAt());
        verify(userSubscriptionRepo).save(existing);
        verify(userSubscriptionRepo, never()).findAllByCreatedByAndProductKeyAndStatusAndActiveTrue(
                any(Long.class), any(), any());
        verify(completionRepo).save(any());
    }
}
