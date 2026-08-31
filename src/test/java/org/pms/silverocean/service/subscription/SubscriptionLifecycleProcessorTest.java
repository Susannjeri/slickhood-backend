package org.pms.silverocean.service.subscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.SubscriptionEventRepo;
import org.pms.silverocean.database.pms.SubscriptionPlanRepo;
import org.pms.silverocean.database.pms.UserSubscriptionRepo;
import org.pms.silverocean.database.pms.entities.SubscriptionEvent;
import org.pms.silverocean.database.pms.entities.SubscriptionPlan;
import org.pms.silverocean.database.pms.entities.UserSubscription;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.subscription.enums.SubscriptionEventType;
import org.pms.silverocean.service.subscription.enums.SubscriptionProduct;
import org.pms.silverocean.service.subscription.enums.SubscriptionPurchaseMode;
import org.pms.silverocean.service.subscription.enums.SubscriptionStatus;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionLifecycleProcessorTest {
    @Mock UserSubscriptionRepo subscriptions;
    @Mock SubscriptionPlanRepo plans;
    @Mock SubscriptionEventRepo events;
    SubscriptionLifecycleProcessor processor;

    @BeforeEach void setUp() { processor = new SubscriptionLifecycleProcessor(subscriptions, plans, events, 30); }

    @Test void cancellationEndsAccessAtBoundary() {
        UserSubscription subscription = active();
        when(events.findTopByUserSubscriptionAndEventTypeAndActiveTrueOrderByCreatedOnDesc(
                subscription, SubscriptionEventType.CANCELLATION_REQUESTED))
                .thenReturn(Optional.of(new SubscriptionEvent()));
        processor.processBoundary(subscription, ZonedDateTime.now());
        assertEquals(SubscriptionStatus.CANCELLED, subscription.getStatus());
        assertFalse(subscription.isActive());
    }

    @Test void autoRenewNeverExtendsAccessWithoutPaymentCallback() {
        UserSubscription subscription = active();
        subscription.setAutoRenew(true);
        processor.processBoundary(subscription, ZonedDateTime.now());
        assertEquals(SubscriptionStatus.EXPIRED, subscription.getStatus());
        verify(events).save(argThat(event -> event.getEventType() == SubscriptionEventType.PAYMENT_FAILED));
    }

    @Test void scheduledFreeDowngradeCreatesPermanentSuccessor() {
        UserSubscription subscription = active();
        SubscriptionEvent change = SubscriptionEvent.builder().eventType(SubscriptionEventType.PLAN_CHANGE_REQUESTED)
                .notes("target:FREE").build();
        change.setActive(true);
        SubscriptionPlan target = SubscriptionPlan.builder().code("FREE").roleFamily(PMSRole.LANDLORD)
                .productKey(SubscriptionProduct.LANDLORD).purchaseMode(SubscriptionPurchaseMode.FREE).build();
        target.setActive(true);
        when(events.findTopByUserSubscriptionAndEventTypeAndActiveTrueOrderByCreatedOnDesc(
                subscription, SubscriptionEventType.PLAN_CHANGE_REQUESTED)).thenReturn(Optional.of(change));
        when(plans.findByCodeAndActiveTrue("FREE")).thenReturn(Optional.of(target));
        processor.processBoundary(subscription, ZonedDateTime.now());
        verify(subscriptions).save(argThat(successor -> successor != subscription
                && "FREE".equals(successor.getPlanCode()) && successor.getEndAt() == null
                && successor.getTermVersion() == 2));
    }

    private UserSubscription active() {
        UserSubscription subscription = UserSubscription.builder().role(PMSRole.LANDLORD)
                .productKey(SubscriptionProduct.LANDLORD).planCode("PAID")
                .status(SubscriptionStatus.ACTIVE).startAt(ZonedDateTime.now().minusMonths(1))
                .endAt(ZonedDateTime.now()).termVersion(1).build();
        subscription.setCreatedBy(7L); subscription.setActive(true);
        when(events.findTopByUserSubscriptionAndEventTypeAndActiveTrueOrderByCreatedOnDesc(
                subscription, SubscriptionEventType.CANCELLATION_REQUESTED)).thenReturn(Optional.empty());
        return subscription;
    }
}
