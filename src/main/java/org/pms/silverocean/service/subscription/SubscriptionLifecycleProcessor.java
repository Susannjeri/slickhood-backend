package org.pms.silverocean.service.subscription;

import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.database.pms.SubscriptionEventRepo;
import org.pms.silverocean.database.pms.SubscriptionPlanRepo;
import org.pms.silverocean.database.pms.UserSubscriptionRepo;
import org.pms.silverocean.database.pms.entities.SubscriptionEvent;
import org.pms.silverocean.database.pms.entities.SubscriptionPlan;
import org.pms.silverocean.database.pms.entities.UserSubscription;
import org.pms.silverocean.service.subscription.enums.SubscriptionEventType;
import org.pms.silverocean.service.subscription.enums.SubscriptionPurchaseMode;
import org.pms.silverocean.service.subscription.enums.SubscriptionStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

/** Applies period-boundary state changes. It never grants paid access without a paid invoice callback. */
@Service
@Slf4j
public class SubscriptionLifecycleProcessor {
    private final UserSubscriptionRepo subscriptions;
    private final SubscriptionPlanRepo plans;
    private final SubscriptionEventRepo events;
    private final int graceDays;

    public SubscriptionLifecycleProcessor(UserSubscriptionRepo subscriptions, SubscriptionPlanRepo plans,
                                          SubscriptionEventRepo events,
                                          @Value("${subscription.upgrade.grace-days-after-expiry:30}") int graceDays) {
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.events = events;
        this.graceDays = Math.max(0, graceDays);
    }

    @Scheduled(cron = "${subscription.lifecycle.cron:0 */5 * * * *}")
    @Transactional
    public void processDueTerms() {
        ZonedDateTime now = ZonedDateTime.now();
        for (UserSubscription subscription : subscriptions
                .findAllByStatusAndActiveTrueAndEndAtLessThanEqual(SubscriptionStatus.ACTIVE, now)) {
            processBoundary(subscription, now);
        }
        ZonedDateTime graceBoundary = now.minusDays(graceDays);
        for (UserSubscription subscription : subscriptions
                .findAllByStatusAndActiveTrueAndEndAtLessThanEqual(SubscriptionStatus.EXPIRED, graceBoundary)) {
            subscription.setStatus(SubscriptionStatus.SUSPENDED);
            subscription.setActive(false);
            subscriptions.save(subscription);
            event(subscription, SubscriptionEventType.EXPIRY, "grace_period_ended");
        }
    }

    void processBoundary(UserSubscription subscription, ZonedDateTime now) {
        if (events.findTopByUserSubscriptionAndEventTypeAndActiveTrueOrderByCreatedOnDesc(
                subscription, SubscriptionEventType.CANCELLATION_REQUESTED).isPresent()) {
            subscription.setStatus(SubscriptionStatus.CANCELLED);
            subscription.setActive(false);
            subscriptions.save(subscription);
            event(subscription, SubscriptionEventType.EXPIRY, "cancelled_at_period_end");
            return;
        }

        var change = events.findTopByUserSubscriptionAndEventTypeAndActiveTrueOrderByCreatedOnDesc(
                subscription, SubscriptionEventType.PLAN_CHANGE_REQUESTED);
        if (change.isPresent()) {
            String notes = change.get().getNotes();
            String targetCode = notes != null && notes.startsWith("target:") ? notes.substring(7) : "";
            SubscriptionPlan target = plans.findByCodeAndActiveTrue(targetCode).orElse(null);
            expire(subscription, target != null && mode(target) == SubscriptionPurchaseMode.FREE
                    ? "scheduled_change_to_free" : "scheduled_change_requires_payment");
            change.get().setActive(false);
            events.save(change.get());
            if (target != null && mode(target) == SubscriptionPurchaseMode.FREE) activateFreeSuccessor(subscription, target, now);
            return;
        }

        // Auto-renew is an instruction to attempt billing, never evidence that billing succeeded.
        expire(subscription, subscription.isAutoRenew() ? "auto_renew_payment_required" : "term_ended");
        if (subscription.isAutoRenew()) event(subscription, SubscriptionEventType.PAYMENT_FAILED,
                "renewal_checkout_required");
    }

    private void expire(UserSubscription subscription, String notes) {
        subscription.setStatus(SubscriptionStatus.EXPIRED);
        // Keep active during the configured grace window; entitlement guards inspect status and deny paid features.
        subscriptions.save(subscription);
        event(subscription, SubscriptionEventType.EXPIRY, notes);
    }

    private void activateFreeSuccessor(UserSubscription previous, SubscriptionPlan target, ZonedDateTime now) {
        UserSubscription successor = UserSubscription.builder()
                .role(target.getRoleFamily()).productKey(target.getProductKey()).planCode(target.getCode())
                .status(SubscriptionStatus.ACTIVE).startAt(now).endAt(null).autoRenew(false)
                .sourcePaymentRef(null).termVersion(previous.getTermVersion() + 1).build();
        successor.setCreatedBy(previous.getCreatedBy()); successor.setActive(true);
        subscriptions.save(successor);
        event(successor, SubscriptionEventType.ACTIVATION, "scheduled_free_plan_change");
    }

    private SubscriptionPurchaseMode mode(SubscriptionPlan plan) {
        if (plan.getPurchaseMode() != null) return plan.getPurchaseMode();
        return plan.getPrice() != null && plan.getPrice().signum() > 0
                ? SubscriptionPurchaseMode.SELF_SERVICE : SubscriptionPurchaseMode.FREE;
    }

    private void event(UserSubscription subscription, SubscriptionEventType type, String notes) {
        SubscriptionEvent event = SubscriptionEvent.builder().userSubscription(subscription)
                .eventType(type).notes(notes).build();
        event.setCreatedBy(subscription.getCreatedBy()); event.setActive(true); events.save(event);
        log.info("Subscription {} lifecycle event {} ({})", subscription.getId(), type, notes);
    }
}
