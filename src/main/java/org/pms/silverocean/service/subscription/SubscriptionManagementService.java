package org.pms.silverocean.service.subscription;

import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.SubscriptionBillingItemDTO;
import org.pms.silverocean.controller.wrappers.SubscriptionCurrentDTO;
import org.pms.silverocean.controller.wrappers.SubscriptionOverviewDTO;
import org.pms.silverocean.database.pms.PMSInvoiceRepo;
import org.pms.silverocean.database.pms.SubscriptionEventRepo;
import org.pms.silverocean.database.pms.SubscriptionPlanRepo;
import org.pms.silverocean.database.pms.UserSubscriptionRepo;
import org.pms.silverocean.database.pms.entities.SubscriptionEvent;
import org.pms.silverocean.database.pms.entities.SubscriptionPlan;
import org.pms.silverocean.database.pms.entities.UserSubscription;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.property.UnitReportDao;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.subscription.enums.SubscriptionEventType;
import org.pms.silverocean.service.subscription.enums.SubscriptionStatus;
import org.pms.silverocean.service.subscription.enums.SubscriptionPurchaseMode;
import org.pms.silverocean.service.subscription.enums.SubscriptionProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Service
public class SubscriptionManagementService {
    private final UserDao userDao;
    private final UserSubscriptionRepo userSubscriptionRepo;
    private final SubscriptionPlanRepo subscriptionPlanRepo;
    private final SubscriptionEventRepo subscriptionEventRepo;
    private final PMSInvoiceRepo invoiceRepo;
    private final UnitReportDao unitReportDao;
    private final SubscriptionProvisioningService provisioningService;
    private final SubscriptionInvoiceService subscriptionInvoiceService;
    private final NotificationService notificationService;

    public SubscriptionManagementService(UserDao userDao, UserSubscriptionRepo userSubscriptionRepo,
                                         SubscriptionPlanRepo subscriptionPlanRepo,
                                         SubscriptionEventRepo subscriptionEventRepo,
                                         PMSInvoiceRepo invoiceRepo, UnitReportDao unitReportDao,
                                         SubscriptionProvisioningService provisioningService,
                                         SubscriptionInvoiceService subscriptionInvoiceService,
                                         NotificationService notificationService) {
        this.userDao = userDao;
        this.userSubscriptionRepo = userSubscriptionRepo;
        this.subscriptionPlanRepo = subscriptionPlanRepo;
        this.subscriptionEventRepo = subscriptionEventRepo;
        this.invoiceRepo = invoiceRepo;
        this.unitReportDao = unitReportDao;
        this.provisioningService = provisioningService;
        this.subscriptionInvoiceService = subscriptionInvoiceService;
        this.notificationService = notificationService;
    }

    @Transactional
    public SubscriptionOverviewDTO overview(String roleValue, String productValue) {
        long userId = currentUserId();
        PMSRole role = parseRole(roleValue);
        SubscriptionProduct product = parseProduct(productValue);
        SubscriptionCurrentDTO current = product == null
                ? provisioningService.getCurrentSubscriptionForSessionRole(role.name())
                : provisioningService.getCurrentSubscriptionForSessionProduct(product.name());
        if (current == null) {
            return new SubscriptionOverviewDTO(null, 0, 0, false, null);
        }
        UserSubscription subscription = latest(userId, role, product);
        boolean cancelling = subscriptionEventRepo
                .findTopByUserSubscriptionAndEventTypeAndActiveTrueOrderByCreatedOnDesc(
                        subscription, SubscriptionEventType.CANCELLATION_REQUESTED)
                .isPresent();
        String scheduledPlanCode = subscriptionEventRepo
                .findTopByUserSubscriptionAndEventTypeAndActiveTrueOrderByCreatedOnDesc(
                        subscription, SubscriptionEventType.PLAN_CHANGE_REQUESTED)
                .map(SubscriptionEvent::getNotes)
                .filter(notes -> notes.startsWith("target:"))
                .map(notes -> notes.substring("target:".length()))
                .orElse(null);
        int propertiesUsed = current.role().equals(PMSRole.LANDLORD.name())
                ? unitReportDao.countPropertiesByOwner(userId) : 0;
        int unitsUsed = current.role().equals(PMSRole.LANDLORD.name())
                ? unitReportDao.countUnitsByOwner(userId) : 0;
        return new SubscriptionOverviewDTO(current, propertiesUsed, unitsUsed, cancelling, scheduledPlanCode);
    }

    @Transactional(readOnly = true)
    public Page<SubscriptionBillingItemDTO> billingHistory(Pageable pageable) {
        return invoiceRepo
                .findByBilledUserIdAndSubscriptionPlanCodeIsNotNullOrderByCreatedOnDesc(currentUserId(), pageable)
                .map(SubscriptionBillingItemDTO::from);
    }

    @Transactional
    public SubscriptionCurrentDTO updateAutoRenew(String roleValue, String productValue, boolean enabled) {
        long userId = currentUserId();
        PMSRole role = parseRole(roleValue);
        SubscriptionProduct product = parseProduct(productValue);
        UserSubscription subscription = active(userId, role, product);
        subscription.setAutoRenew(enabled);
        userSubscriptionRepo.save(subscription);
        saveEvent(subscription, SubscriptionEventType.AUTO_RENEW_UPDATED, "enabled:" + enabled, userId);
        return product == null ? provisioningService.getCurrentSubscriptionForSessionRole(role.name())
                : provisioningService.getCurrentSubscriptionForSessionProduct(product.name());
    }

    @Transactional
    public SubscriptionOverviewDTO scheduleCancellation(String roleValue, String productValue, String reason) {
        long userId = currentUserId();
        PMSRole role = parseRole(roleValue);
        SubscriptionProduct product = parseProduct(productValue);
        UserSubscription subscription = active(userId, role, product);
        if (subscription.getEndAt() == null) {
            SubscriptionPlan plan = plan(subscription.getPlanCode());
            subscription.setEndAt(SubscriptionTerms.endAt(plan.getBillingCycle(), ZonedDateTime.now()));
            userSubscriptionRepo.save(subscription);
        }
        if (subscriptionEventRepo.findTopByUserSubscriptionAndEventTypeAndActiveTrueOrderByCreatedOnDesc(
                subscription, SubscriptionEventType.CANCELLATION_REQUESTED).isEmpty()) {
            String notes = StringUtils.isBlank(reason) ? "cancel_at_period_end" : "cancel_at_period_end:" + reason.trim();
            saveEvent(subscription, SubscriptionEventType.CANCELLATION_REQUESTED, notes, userId);
        }
        deactivateEvents(subscription, SubscriptionEventType.PLAN_CHANGE_REQUESTED);
        subscription.setAutoRenew(false);
        userSubscriptionRepo.save(subscription);
        return overview(role.name(), productValue);
    }

    @Transactional
    public SubscriptionOverviewDTO restoreCancellation(String roleValue, String productValue) {
        long userId = currentUserId();
        PMSRole role = parseRole(roleValue);
        SubscriptionProduct product = parseProduct(productValue);
        UserSubscription subscription = active(userId, role, product);
        deactivateEvents(subscription, SubscriptionEventType.CANCELLATION_REQUESTED);
        saveEvent(subscription, SubscriptionEventType.CANCELLATION_REVOKED, "restored", userId);
        return overview(role.name(), productValue);
    }

    @Transactional
    public SubscriptionOverviewDTO schedulePlanChange(String roleValue, String productValue, String targetPlanCode) {
        long userId = currentUserId();
        PMSRole role = parseRole(roleValue);
        SubscriptionProduct product = parseProduct(productValue);
        UserSubscription subscription = active(userId, role, product);
        SubscriptionPlan currentPlan = plan(subscription.getPlanCode());
        SubscriptionPlan targetPlan = plan(targetPlanCode);
        if (targetPlan.getRoleFamily() != subscription.getRole()
                || targetPlan.getProductKey() != currentPlan.getProductKey()
                || tierRank(targetPlan) >= tierRank(currentPlan)) {
            throw new PMSCustomException(ResponseCode.GENERAL_FAILURE);
        }
        deactivateEvents(subscription, SubscriptionEventType.PLAN_CHANGE_REQUESTED);
        deactivateEvents(subscription, SubscriptionEventType.CANCELLATION_REQUESTED);
        saveEvent(subscription, SubscriptionEventType.PLAN_CHANGE_REQUESTED,
                "target:" + targetPlan.getCode(), userId);
        subscription.setAutoRenew(false);
        userSubscriptionRepo.save(subscription);
        return overview(role.name(), productValue);
    }

    @Transactional
    public SubscriptionOverviewDTO revokePlanChange(String roleValue, String productValue) {
        long userId = currentUserId();
        PMSRole role = parseRole(roleValue);
        SubscriptionProduct product = parseProduct(productValue);
        UserSubscription subscription = active(userId, role, product);
        deactivateEvents(subscription, SubscriptionEventType.PLAN_CHANGE_REQUESTED);
        saveEvent(subscription, SubscriptionEventType.PLAN_CHANGE_REVOKED, "revoked", userId);
        return overview(role.name(), productValue);
    }

    @Transactional
    public SubscriptionMutationResult renew(String roleValue, String productValue, Long paymentAccountId) {
        long userId = currentUserId();
        PMSRole role = parseRole(roleValue);
        UserSubscription subscription = latest(userId, role, parseProduct(productValue));
        SubscriptionPlan plan = plan(subscription.getPlanCode());
        SubscriptionPurchaseMode mode = purchaseMode(plan);
        if (mode == SubscriptionPurchaseMode.SALES_MANAGED) {
            throw new PMSCustomException(ResponseCode.SUBSCRIPTION_SALES_MANAGED_REQUIRED);
        }
        boolean paid = mode == SubscriptionPurchaseMode.SELF_SERVICE;
        if (paid) {
            if (paymentAccountId == null || paymentAccountId <= 0) {
                throw new PMSCustomException(ResponseCode.SUBSCRIPTION_PAYMENT_PAYEE_NOT_CONFIGURED);
            }
            return SubscriptionMutationResult.pendingInvoice(subscriptionInvoiceService.createSubscriptionCheckout(
                    userId, plan, subscription.getRole(), paymentAccountId));
        }

        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setActive(true);
        subscription.setStartAt(subscription.getStartAt() == null ? ZonedDateTime.now() : subscription.getStartAt());
        subscription.setEndAt(null);
        subscription.setTermVersion(subscription.getTermVersion() + 1);
        userSubscriptionRepo.save(subscription);
        saveEvent(subscription, SubscriptionEventType.RENEWAL, "free_renewal", userId);
        return SubscriptionMutationResult.freeAssigned(
                provisioningService.getCurrentSubscriptionForSessionProduct(subscription.getProductKey().name()));
    }

    public void requestSalesContact(String planCode, String message) {
        long userId = currentUserId();
        SubscriptionPlan requestedPlan = plan(planCode);
        String safeMessage = StringUtils.defaultIfBlank(message, "No additional message provided.").trim();
        String formatted = "Subscription sales request\nUser ID: " + userId
                + "\nEmail: " + userDao.getEmail()
                + "\nPlan: " + requestedPlan.getDisplayName() + " (" + requestedPlan.getCode() + ")"
                + "\nMessage: " + safeMessage;
        notificationService.sendEmailToSuperAdmin(NotificationType.SUBSCRIPTION_SALES_REQUEST_EMAIL, formatted);
    }

    private long currentUserId() {
        Long userId = userDao.getUserId();
        if (userId == null) {
            throw new PMSCustomException(ResponseCode.COULD_NOT_FIND_USER_SESSION);
        }
        return userId;
    }

    private UserSubscription active(long userId, PMSRole role, SubscriptionProduct product) {
        if (product != null) {
            return userSubscriptionRepo.findTopByCreatedByAndProductKeyAndStatusAndActiveTrueOrderByStartAtDesc(
                            userId, product, SubscriptionStatus.ACTIVE)
                    .orElseThrow(() -> new PMSCustomException(ResponseCode.SUBSCRIPTION_CURRENT_ABSENT));
        }
        return userSubscriptionRepo.findTopByCreatedByAndRoleAndStatusAndActiveTrueOrderByStartAtDesc(
                        userId, role, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SUBSCRIPTION_CURRENT_ABSENT));
    }

    private UserSubscription latest(long userId, PMSRole role, SubscriptionProduct product) {
        if (product != null) {
            return userSubscriptionRepo.findTopByCreatedByAndProductKeyOrderByStartAtDesc(userId, product)
                    .orElseThrow(() -> new PMSCustomException(ResponseCode.SUBSCRIPTION_CURRENT_ABSENT));
        }
        return userSubscriptionRepo.findTopByCreatedByAndRoleOrderByStartAtDesc(userId, role)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SUBSCRIPTION_CURRENT_ABSENT));
    }

    private SubscriptionPlan plan(String code) {
        return subscriptionPlanRepo.findByCodeAndActiveTrue(code.trim().toUpperCase())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SUBSCRIPTION_PLAN_NOT_FOUND));
    }

    private PMSRole parseRole(String roleValue) {
        if (StringUtils.isBlank(roleValue)) {
            throw new PMSCustomException(ResponseCode.INVALID_ROLE);
        }
        try {
            return PMSRole.valueOf(roleValue.trim().toUpperCase());
        } catch (IllegalArgumentException error) {
            throw new PMSCustomException(ResponseCode.INVALID_ROLE);
        }
    }

    private SubscriptionProduct parseProduct(String productValue) {
        if (StringUtils.isBlank(productValue)) return null;
        try { return SubscriptionProduct.valueOf(productValue.trim().toUpperCase()); }
        catch (IllegalArgumentException error) {
            throw new PMSCustomException(ResponseCode.SUBSCRIPTION_PLAN_PRODUCT_MISMATCH);
        }
    }

    private static BigDecimal priceOrZero(SubscriptionPlan plan) {
        return plan.getPrice() == null ? BigDecimal.ZERO : plan.getPrice();
    }

    private SubscriptionPurchaseMode purchaseMode(SubscriptionPlan plan) {
        if (plan.getPurchaseMode() != null) return plan.getPurchaseMode();
        if (plan.getCode().contains("CUSTOM")) return SubscriptionPurchaseMode.SALES_MANAGED;
        return priceOrZero(plan).signum() > 0 ? SubscriptionPurchaseMode.SELF_SERVICE : SubscriptionPurchaseMode.FREE;
    }

    private int tierRank(SubscriptionPlan plan) {
        return plan.getTierRank() == null ? 0 : plan.getTierRank();
    }

    private void saveEvent(UserSubscription subscription, SubscriptionEventType type, String notes, long userId) {
        SubscriptionEvent event = SubscriptionEvent.builder()
                .eventType(type).notes(notes).userSubscription(subscription).build();
        event.setCreatedBy(userId);
        event.setActive(true);
        subscriptionEventRepo.save(event);
    }

    private void deactivateEvents(UserSubscription subscription, SubscriptionEventType type) {
        subscriptionEventRepo.findAllByUserSubscriptionAndEventTypeAndActiveTrue(subscription, type)
                .forEach(event -> {
                    event.setActive(false);
                    subscriptionEventRepo.save(event);
                });
    }
}
