package org.pms.silverocean.service.subscription;

import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.database.pms.RoleRepo;
import org.pms.silverocean.database.pms.SubscriptionEventRepo;
import org.pms.silverocean.database.pms.SubscriptionPaymentCompletionRepo;
import org.pms.silverocean.database.pms.SubscriptionPlanRepo;
import org.pms.silverocean.database.pms.UserRoleRepo;
import org.pms.silverocean.database.pms.UserSubscriptionRepo;
import org.pms.silverocean.database.pms.entities.Role;
import org.pms.silverocean.database.pms.entities.SubscriptionEvent;
import org.pms.silverocean.database.pms.entities.SubscriptionPaymentCompletion;
import org.pms.silverocean.database.pms.entities.SubscriptionPlan;
import org.pms.silverocean.database.pms.entities.UserSubscription;
import org.pms.silverocean.service.subscription.enums.SubscriptionEventType;
import org.pms.silverocean.service.subscription.enums.SubscriptionStatus;
import org.pms.silverocean.service.subscription.enums.SubscriptionProduct;
import org.pms.silverocean.service.subscription.enums.SubscriptionPurchaseMode;
import org.pms.silverocean.service.payment.contract.PaidInvoiceReader;
import org.pms.silverocean.service.payment.contract.PaidInvoiceView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Slf4j
public class SubscriptionPaymentCompletionService {

    private final PaidInvoiceReader paidInvoiceReader;
    private final SubscriptionPaymentCompletionRepo completionRepo;
    private final SubscriptionPlanRepo subscriptionPlanRepo;
    private final UserSubscriptionRepo userSubscriptionRepo;
    private final SubscriptionEventRepo subscriptionEventRepo;
    private final RoleRepo roleRepo;
    private final UserRoleRepo userRoleRepo;

    public SubscriptionPaymentCompletionService(
            PaidInvoiceReader paidInvoiceReader,
            SubscriptionPaymentCompletionRepo completionRepo,
            SubscriptionPlanRepo subscriptionPlanRepo,
            UserSubscriptionRepo userSubscriptionRepo,
            SubscriptionEventRepo subscriptionEventRepo,
            RoleRepo roleRepo,
            UserRoleRepo userRoleRepo
    ) {
        this.paidInvoiceReader = paidInvoiceReader;
        this.completionRepo = completionRepo;
        this.subscriptionPlanRepo = subscriptionPlanRepo;
        this.userSubscriptionRepo = userSubscriptionRepo;
        this.subscriptionEventRepo = subscriptionEventRepo;
        this.roleRepo = roleRepo;
        this.userRoleRepo = userRoleRepo;
    }

    /**
     * After an invoice is fully paid, assigns the subscription plan once per invoice (idempotent).
     */
    @Transactional
    public void completePaidSubscriptionAfterPayment(long invoiceId, String providerReference) {
        PaidInvoiceView invoice = paidInvoiceReader.findByIdForUpdate(invoiceId).orElse(null);
        if (invoice == null) {
            throw new IllegalStateException("Paid invoice "+invoiceId+" is not available to subscription activation");
        }
        String planCode = invoice.getSubscriptionPlanCode();
        if (planCode == null || planCode.isBlank()) {
            return;
        }
        if (!invoice.isPaid() || invoice.getPendingAmount() > 0) {
            throw new IllegalStateException("Invoice "+invoice.getRef()+" emitted a paid event before full settlement");
        }
        if (completionRepo.existsByInvoiceId(invoice.getId())) {
            return;
        }

        String normalizedPlanCode = planCode.trim().toUpperCase(Locale.ROOT);
        Optional<SubscriptionPlan> planOpt = subscriptionPlanRepo.findByCodeAndActiveTrue(normalizedPlanCode);
        if (planOpt.isEmpty()) {
            log.error("Paid subscription invoice {} references unknown or inactive plan {}", invoice.getRef(), normalizedPlanCode);
            throw new IllegalStateException("Paid subscription plan is unknown or inactive: "+normalizedPlanCode);
        }
        SubscriptionPlan plan = planOpt.get();
        if (plan.getPurchaseMode() != null && plan.getPurchaseMode() != SubscriptionPurchaseMode.SELF_SERVICE) {
            throw new IllegalStateException("Paid invoice references a non-self-service plan: "+normalizedPlanCode);
        }
        if (plan.getPrice() == null || plan.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Invoice {} marked subscription but plan {} is not priced; skipping activation", invoice.getRef(), normalizedPlanCode);
            throw new IllegalStateException("Paid invoice references an unpriced plan: "+normalizedPlanCode);
        }

        long subscriberId = invoice.getBilledUserId();
        Role dbRole = roleRepo.findByName(plan.getRoleFamily().getName()).orElse(null);
        if (dbRole == null || !dbRole.isActive()) {
            log.error("Cannot activate subscription for invoice {}: role {} missing", invoice.getRef(), plan.getRoleFamily());
            throw new IllegalStateException("Subscription role is missing or inactive: "+plan.getRoleFamily());
        }
        if (userRoleRepo.findByUserIdAndRoleId(subscriberId, dbRole.getId()) == 0) {
            log.error("Cannot activate subscription for invoice {}: user {} no longer holds role {}", invoice.getRef(), subscriberId, plan.getRoleFamily());
            throw new IllegalStateException("Subscriber no longer holds required role: "+plan.getRoleFamily());
        }

        SubscriptionProduct product = product(plan);
        Optional<UserSubscription> existingActive = findActiveSubscription(subscriberId, product);
        boolean renewal = existingActive
                .filter(existing -> existing.getPlanCode().equalsIgnoreCase(plan.getCode()))
                .isPresent();
        SubscriptionEventType eventType = renewal
                ? SubscriptionEventType.RENEWAL
                : existingActive.isPresent() ? SubscriptionEventType.UPGRADE : SubscriptionEventType.ACTIVATION;

        if (renewal) {
            UserSubscription existing = existingActive.orElseThrow();
            java.time.ZonedDateTime termStart = existing.getEndAt() != null
                    && existing.getEndAt().isAfter(java.time.ZonedDateTime.now())
                    ? existing.getEndAt()
                    : java.time.ZonedDateTime.now();
            existing.setEndAt(SubscriptionTerms.endAt(plan.getBillingCycle(), termStart));
            existing.setSourcePaymentRef(normalizeProviderRef(providerReference));
            existing.setTermVersion(existing.getTermVersion() + 1);
            userSubscriptionRepo.save(existing);
            saveEvent(existing, eventType, invoice, subscriberId);
            saveCompletionMarker(invoice, subscriberId, plan, providerReference);
            return;
        }

        deactivateActiveSubscriptions(subscriberId, product);

        java.time.ZonedDateTime startAt = java.time.ZonedDateTime.now();
        UserSubscription sub = UserSubscription.builder()
                .role(plan.getRoleFamily())
                .planCode(plan.getCode())
                .productKey(product)
                .status(SubscriptionStatus.ACTIVE)
                .startAt(startAt)
                .endAt(SubscriptionTerms.endAt(plan.getBillingCycle(), startAt))
                .autoRenew(false)
                .sourcePaymentRef(normalizeProviderRef(providerReference))
                .termVersion(1L)
                .build();
        sub.setCreatedBy(subscriberId);
        sub.setActive(true);
        UserSubscription saved = userSubscriptionRepo.save(sub);

        saveEvent(saved, eventType, invoice, subscriberId);
        saveCompletionMarker(invoice, subscriberId, plan, providerReference);
    }

    private void saveCompletionMarker(PaidInvoiceView invoice, long subscriberId, SubscriptionPlan plan,
                                      String providerReference) {
        SubscriptionPaymentCompletion marker = SubscriptionPaymentCompletion.builder()
                .invoiceId(invoice.getId())
                .subscriberUserId(subscriberId)
                .planCode(plan.getCode())
                .providerReference(normalizeProviderRef(providerReference))
                .build();
        marker.setActive(true);
        completionRepo.save(marker);
    }

    private void saveEvent(UserSubscription subscription, SubscriptionEventType eventType,
                           PaidInvoiceView invoice, long subscriberId) {
        SubscriptionEvent evt = SubscriptionEvent.builder()
                .eventType(eventType)
                .notes("paid_invoice:" + invoice.getRef())
                .userSubscription(subscription)
                .build();
        evt.setCreatedBy(subscriberId);
        evt.setActive(true);
        subscriptionEventRepo.save(evt);
    }

    private static String normalizeProviderRef(String providerReference) {
        if (providerReference == null || providerReference.isBlank()) {
            return "UNKNOWN";
        }
        return providerReference.trim();
    }

    private Optional<UserSubscription> findActiveSubscription(long subscriberUserId, SubscriptionProduct product) {
        return userSubscriptionRepo.findTopByCreatedByAndProductKeyAndStatusAndActiveTrueOrderByStartAtDesc(
                subscriberUserId, product, SubscriptionStatus.ACTIVE);
    }

    private void deactivateActiveSubscriptions(long subscriberUserId, SubscriptionProduct product) {
        List<UserSubscription> active = userSubscriptionRepo.findAllByCreatedByAndProductKeyAndStatusAndActiveTrue(
                subscriberUserId, product, SubscriptionStatus.ACTIVE);
        for (UserSubscription us : active) {
            us.setStatus(SubscriptionStatus.CANCELLED);
            us.setActive(false);
            userSubscriptionRepo.save(us);
        }
    }

    private SubscriptionProduct product(SubscriptionPlan plan) {
        if (plan.getProductKey() != null) return plan.getProductKey();
        return switch (plan.getPlanCategory()) {
            case LANDLORD -> SubscriptionProduct.LANDLORD;
            case ESTATE_MANAGEMENT -> SubscriptionProduct.ESTATE_MANAGEMENT;
            case PROPERTY_SALES -> SubscriptionProduct.PROPERTY_SALES;
            case ASSET_PORTFOLIO_MANAGER -> SubscriptionProduct.MY_WEALTH;
            case AFFILIATE -> SubscriptionProduct.AFFILIATE;
            case SERVICE_PROVIDER -> SubscriptionProduct.SERVICES;
        };
    }
}
