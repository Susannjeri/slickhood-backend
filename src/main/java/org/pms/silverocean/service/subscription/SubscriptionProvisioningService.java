package org.pms.silverocean.service.subscription;

import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.RoleRepo;
import org.pms.silverocean.database.pms.SubscriptionEventRepo;
import org.pms.silverocean.database.pms.SubscriptionPlanRepo;
import org.pms.silverocean.database.pms.UserSubscriptionRepo;
import org.pms.silverocean.database.pms.entities.Role;
import org.pms.silverocean.database.pms.entities.SubscriptionEvent;
import org.pms.silverocean.database.pms.entities.SubscriptionPlan;
import org.pms.silverocean.database.pms.entities.UserSubscription;
import org.pms.silverocean.controller.wrappers.SubscriptionCurrentDTO;
import org.pms.silverocean.controller.wrappers.SubscriptionPlanSummaryRestDTO;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.subscription.enums.SubscriptionEventType;
import org.pms.silverocean.service.subscription.enums.SubscriptionStatus;
import org.pms.silverocean.database.pms.UserRoleRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Slf4j
public class SubscriptionProvisioningService {

    private final UserDao userDao;
    private final RoleRepo roleRepo;
    private final SubscriptionPlanRepo subscriptionPlanRepo;
    private final UserSubscriptionRepo userSubscriptionRepo;
    private final SubscriptionEventRepo subscriptionEventRepo;
    private final DefaultFreePlanCodeResolver defaultFreePlanCodeResolver;
    private final SubscriptionPlanService subscriptionPlanService;
    private final UserRoleRepo userRoleRepo;
    private final SubscriptionInvoiceService subscriptionInvoiceService;
    private final int graceDaysAfterExpiry;
    private final int trialDays;

    public SubscriptionProvisioningService(
            UserDao userDao,
            RoleRepo roleRepo,
            UserRoleRepo userRoleRepo,
            SubscriptionPlanRepo subscriptionPlanRepo,
            UserSubscriptionRepo userSubscriptionRepo,
            SubscriptionEventRepo subscriptionEventRepo,
            DefaultFreePlanCodeResolver defaultFreePlanCodeResolver,
            SubscriptionPlanService subscriptionPlanService,
            SubscriptionInvoiceService subscriptionInvoiceService,
            @Value("${subscription.upgrade.grace-days-after-expiry:30}") int graceDaysAfterExpiry,
            @Value("${subscription.trial.days:14}") int trialDays
    ) {
        this.userDao = userDao;
        this.roleRepo = roleRepo;
        this.userRoleRepo = userRoleRepo;
        this.subscriptionPlanRepo = subscriptionPlanRepo;
        this.userSubscriptionRepo = userSubscriptionRepo;
        this.subscriptionEventRepo = subscriptionEventRepo;
        this.defaultFreePlanCodeResolver = defaultFreePlanCodeResolver;
        this.subscriptionPlanService = subscriptionPlanService;
        this.subscriptionInvoiceService = subscriptionInvoiceService;
        this.graceDaysAfterExpiry = graceDaysAfterExpiry;
        this.trialDays = Math.max(1, trialDays);
    }

    public int getTrialDays() {
        return trialDays;
    }

    /**
     * Starts the account's one-time trial after registration and email verification.
     * Role assignment deliberately does not call this method: the user must first
     * choose a business area and plan in the onboarding UI.
     */
    @Transactional
    public SubscriptionCurrentDTO startTrialForSessionUser(String roleValue, String planCode) {
        Long userId = userDao.getUserId();
        if (userId == null) {
            throw new PMSCustomException(ResponseCode.COULD_NOT_FIND_USER_SESSION);
        }
        PMSRole role = parsePmsRoleConstant(roleValue);
        if (userSubscriptionRepo.findTopByCreatedByAndRoleOrderByStartAtDesc(userId, role).isPresent()) {
            throw new PMSCustomException(ResponseCode.SUBSCRIPTION_TRIAL_ALREADY_USED);
        }
        validateCatalogRole(role);
        Role dbRole = roleRepo.findByName(role.getName())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_ROLE));
        if (userRoleRepo.findByUserIdAndRoleId(userId, dbRole.getId()) == 0) {
            throw new PMSCustomException(ResponseCode.SUBSCRIPTION_ROLE_NOT_HELD_BY_USER);
        }

        SubscriptionPlan plan = subscriptionPlanRepo.findByCode(normalizePlanCode(planCode))
                .filter(SubscriptionPlan::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SUBSCRIPTION_PLAN_NOT_FOUND));
        if (!role.equals(plan.getRoleFamily())) {
            throw new PMSCustomException(ResponseCode.SUBSCRIPTION_PLAN_ROLE_MISMATCH);
        }
        if (plan.getCode().contains("CUSTOM")) {
            throw new PMSCustomException(ResponseCode.SUBSCRIPTION_TRIAL_NOT_AVAILABLE);
        }

        ZonedDateTime startAt = ZonedDateTime.now();
        UserSubscription subscription = UserSubscription.builder()
                .role(role)
                .planCode(plan.getCode())
                .status(SubscriptionStatus.ACTIVE)
                .startAt(startAt)
                .endAt(startAt.plusDays(trialDays))
                .autoRenew(false)
                .sourcePaymentRef(null)
                .build();
        subscription.setCreatedBy(userId);
        subscription.setActive(true);
        UserSubscription saved = userSubscriptionRepo.save(subscription);

        SubscriptionEvent event = SubscriptionEvent.builder()
                .eventType(SubscriptionEventType.TRIAL_STARTED)
                .notes("trial_days:" + trialDays)
                .userSubscription(saved)
                .build();
        event.setCreatedBy(userId);
        event.setActive(true);
        subscriptionEventRepo.save(event);

        return SubscriptionCurrentDTO.from(saved,
                SubscriptionPlanSummaryRestDTO.from(subscriptionPlanService.getPlanByCode(plan.getCode())));
    }

    /**
     * Self-service subscribe/upgrade for the session user.
     */
    @Transactional
    public SubscriptionMutationResult subscribeOrUpgradeForSessionUser(
            String role,
            String planCode,
            Long paymentAccountId
    ) {
        Long userId = userDao.getUserId();
        if (userId == null) {
            throw new PMSCustomException(ResponseCode.COULD_NOT_FIND_USER_SESSION);
        }
        SubscribeOrUpgradeRequestDTO dto = new SubscribeOrUpgradeRequestDTO(
                parsePmsRoleConstant(role), planCode, paymentAccountId);
        return subscribeOrUpgradeForUser(userId, userId, dto);
    }

    @Transactional
    public SubscriptionCurrentDTO getCurrentSubscription() {
        Long userIdObj = userDao.getUserId();
        if (userIdObj == null) {
            throw new PMSCustomException(ResponseCode.COULD_NOT_FIND_USER_SESSION);
        }
        return getCurrentSubscription(userIdObj);
    }

    @Transactional
    public SubscriptionCurrentDTO getCurrentSubscription(Long userId) {
        Optional<UserSubscription> latest = userSubscriptionRepo.findTopByCreatedByOrderByStartAtDesc(userId);
        if (latest.isEmpty()) {
            return null;
        }
        UserSubscription sub = latest.get();
        ZonedDateTime now = ZonedDateTime.now();
        if (sub.getStatus() == SubscriptionStatus.ACTIVE && sub.isActive()
                && sub.getEndAt() != null && !sub.getEndAt().isAfter(now)) {
            long daysExpired = ChronoUnit.DAYS.between(sub.getEndAt().toLocalDate(), now.toLocalDate());
            boolean cancellationScheduled = subscriptionEventRepo
                    .findTopByUserSubscriptionAndEventTypeAndActiveTrueOrderByCreatedOnDesc(
                            sub, SubscriptionEventType.CANCELLATION_REQUESTED)
                    .isPresent();
            sub.setStatus(cancellationScheduled
                    ? SubscriptionStatus.CANCELLED
                    : daysExpired > graceDaysAfterExpiry
                        ? SubscriptionStatus.SUSPENDED
                        : SubscriptionStatus.EXPIRED);
            sub.setActive(false);
            userSubscriptionRepo.save(sub);
        }
        SubscriptionPlanResponseDTO plan = subscriptionPlanService.getPlanByCode(sub.getPlanCode());
        return SubscriptionCurrentDTO.from(sub, SubscriptionPlanSummaryRestDTO.from(plan));
    }

    @Transactional
    public SubscriptionCurrentDTO getCurrentSubscriptionForSessionRole(String roleValue) {
        Long userId = userDao.getUserId();
        if (userId == null) {
            throw new PMSCustomException(ResponseCode.COULD_NOT_FIND_USER_SESSION);
        }
        PMSRole role = parsePmsRoleConstant(roleValue);
        Optional<UserSubscription> latest = userSubscriptionRepo
                .findTopByCreatedByAndRoleOrderByStartAtDesc(userId, role);
        if (latest.isEmpty()) {
            return null;
        }
        return refreshAndMap(latest.get());
    }

    private SubscriptionCurrentDTO refreshAndMap(UserSubscription sub) {
        ZonedDateTime now = ZonedDateTime.now();
        if (sub.getStatus() == SubscriptionStatus.ACTIVE && sub.isActive()
                && sub.getEndAt() != null && !sub.getEndAt().isAfter(now)) {
            long daysExpired = ChronoUnit.DAYS.between(sub.getEndAt().toLocalDate(), now.toLocalDate());
            boolean cancellationScheduled = subscriptionEventRepo
                    .findTopByUserSubscriptionAndEventTypeAndActiveTrueOrderByCreatedOnDesc(
                            sub, SubscriptionEventType.CANCELLATION_REQUESTED)
                    .isPresent();
            sub.setStatus(cancellationScheduled
                    ? SubscriptionStatus.CANCELLED
                    : daysExpired > graceDaysAfterExpiry ? SubscriptionStatus.SUSPENDED : SubscriptionStatus.EXPIRED);
            sub.setActive(false);
            userSubscriptionRepo.save(sub);
        }
        SubscriptionPlanResponseDTO plan = subscriptionPlanService.getPlanByCode(sub.getPlanCode());
        return SubscriptionCurrentDTO.from(sub, SubscriptionPlanSummaryRestDTO.from(plan));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponseDTO> listPlansForRole(PMSRole role) {
        validateCatalogRole(role);
        return subscriptionPlanRepo.findByRoleFamilyAndActiveTrueOrderByPriceAsc(role)
                .stream()
                .map(p -> subscriptionPlanService.getPlanByCode(p.getCode()))
                .toList();
    }

    /** HTTP-facing catalog (string-only summaries; enum constant {@code role} e.g. LANDLORD). */
    @Transactional(readOnly = true)
    public List<SubscriptionPlanSummaryRestDTO> listPlansSummariesForRoleParam(String role) {
        return listPlansForRole(parsePmsRoleConstant(role)).stream()
                .map(SubscriptionPlanSummaryRestDTO::from)
                .toList();
    }

    private SubscriptionMutationResult subscribeOrUpgradeForUser(long actorUserId, long subscriberUserId, SubscribeOrUpgradeRequestDTO request) {
        PMSRole role = request.role();
        validateCatalogRole(role);
        Role dbRole = roleRepo.findByName(role.getName()).orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_ROLE));
        if (userRoleRepo.findByUserIdAndRoleId(subscriberUserId, dbRole.getId()) == 0) {
            throw new PMSCustomException(ResponseCode.SUBSCRIPTION_ROLE_NOT_HELD_BY_USER);
        }
        SubscriptionPlan plan = subscriptionPlanRepo.findByCode(normalizePlanCode(request.planCode()))
                .filter(SubscriptionPlan::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SUBSCRIPTION_PLAN_NOT_FOUND));
        if (!role.equals(plan.getRoleFamily())) {
            throw new PMSCustomException(ResponseCode.SUBSCRIPTION_PLAN_ROLE_MISMATCH);
        }
        validateSubscriptionPlanChangeRules(subscriberUserId, plan);
        boolean hasPayment = plan.getPrice() != null && plan.getPrice().compareTo(BigDecimal.ZERO) > 0;
        if (hasPayment) {
            if (request.paymentAccountId() == null || request.paymentAccountId() <= 0) {
                throw new PMSCustomException(ResponseCode.SUBSCRIPTION_PAYMENT_PAYEE_NOT_CONFIGURED);
            }
            SubscriptionPendingCheckoutDTO checkout = subscriptionInvoiceService.createSubscriptionCheckout(
                    subscriberUserId,
                    plan,
                    role,
                    request.paymentAccountId());
            return SubscriptionMutationResult.pendingInvoice(checkout);
        }
        Optional<UserSubscription> existing = findActiveSubscription(subscriberUserId, role);
        SubscriptionEventType eventType = existing.isPresent()
                ? SubscriptionEventType.UPGRADE
                : SubscriptionEventType.ACTIVATION;
        deactivateActiveSubscriptions(subscriberUserId, role);
        activateFreeSubscription(actorUserId, subscriberUserId, role, plan, eventType,
                eventType == SubscriptionEventType.UPGRADE ? "upgrade_request" : "subscribe_request");
        UserSubscription refreshed = findActiveSubscription(subscriberUserId, role).orElseThrow();
        return SubscriptionMutationResult.freeAssigned(SubscriptionCurrentDTO.from(
                refreshed,
                SubscriptionPlanSummaryRestDTO.from(subscriptionPlanService.getPlanByCode(refreshed.getPlanCode()))));
    }

    private void activateFreeSubscription(
            long actorUserId,
            long subscriberUserId,
            PMSRole role,
            SubscriptionPlan plan,
            SubscriptionEventType eventType,
            String notes
    ) {
        java.time.ZonedDateTime startAt = java.time.ZonedDateTime.now();
        UserSubscription sub = UserSubscription.builder()
                .role(role)
                .planCode(plan.getCode())
                .status(SubscriptionStatus.ACTIVE)
                .startAt(startAt)
                .endAt(SubscriptionTerms.endAt(plan.getBillingCycle(), startAt))
                .autoRenew(false)
                .sourcePaymentRef(null)
                .build();
        sub.setCreatedBy(subscriberUserId);
        sub.setActive(true);
        UserSubscription saved = userSubscriptionRepo.save(sub);
        SubscriptionEvent evt = SubscriptionEvent.builder()
                .eventType(eventType)
                .notes(notes == null ? eventType.name() : notes)
                .userSubscription(saved)
                .build();
        evt.setCreatedBy(actorUserId);
        evt.setActive(true);
        subscriptionEventRepo.save(evt);
    }

    private void deactivateActiveSubscriptions(long subscriberUserId, PMSRole role) {
        List<UserSubscription> active = userSubscriptionRepo.findAllByCreatedByAndRoleAndStatusAndActiveTrue(
                subscriberUserId, role, SubscriptionStatus.ACTIVE);
        for (UserSubscription us : active) {
            us.setStatus(SubscriptionStatus.CANCELLED);
            us.setActive(false);
            userSubscriptionRepo.save(us);
        }
    }

    private Optional<UserSubscription> findActiveSubscription(long subscriberUserId, PMSRole role) {
        return userSubscriptionRepo.findTopByCreatedByAndRoleAndStatusAndActiveTrueOrderByStartAtDesc(
                subscriberUserId, role, SubscriptionStatus.ACTIVE);
    }

    private void validateCatalogRole(PMSRole role) {
        if (!defaultFreePlanCodeResolver.isProvisioningRole(role)) {
            throw new PMSCustomException(ResponseCode.SUBSCRIPTION_CATALOG_ROLE_NOT_ALLOWED);
        }
    }

    /**
     * Free: may move to a strictly higher-priced plan anytime.
     * Paid and not expired: no downgrade to free or same/lower paid tier.
     * No active subscription (Expired): within grace period days after the last subscription end, any paid plan is allowed; after that window only free plans are allowed.
     */
    private void validateSubscriptionPlanChangeRules(long subscriberUserId, SubscriptionPlan targetPlan) {
        ZonedDateTime now = ZonedDateTime.now();
        BigDecimal targetPrice = priceOrZero(targetPlan);

        Optional<UserSubscription> activeOpt = findActiveSubscription(subscriberUserId, targetPlan.getRoleFamily());
        if (activeOpt.isPresent()) {
            UserSubscription us = activeOpt.get();
            boolean expiredByTime = us.getEndAt() != null && us.getEndAt().isBefore(now);
            if (us.getStatus() == SubscriptionStatus.ACTIVE && us.isActive() && !expiredByTime) {
                validateUpgradeWhileActiveNotExpired(us, targetPrice);
                return;
            }
            ZonedDateTime referenceEnd = us.getEndAt() != null ? us.getEndAt() : us.getStartAt();
            validatePostExpiryGraceWindow(referenceEnd, now, targetPrice);
            return;
        }

        Optional<UserSubscription> latest = userSubscriptionRepo.findTopByCreatedByAndRoleOrderByStartAtDesc(
                subscriberUserId, targetPlan.getRoleFamily());
        if (latest.isEmpty()) {
            return;
        }
        UserSubscription last = latest.get();
        ZonedDateTime referenceEnd = last.getEndAt() != null ? last.getEndAt() : last.getStartAt();
        validatePostExpiryGraceWindow(referenceEnd, now, targetPrice);
    }

    private void validateUpgradeWhileActiveNotExpired(UserSubscription currentSub, BigDecimal targetPrice) {
        SubscriptionPlan currentPlan = subscriptionPlanRepo.findByCode(currentSub.getPlanCode()).orElse(null);
        BigDecimal currentPrice = priceOrZero(currentPlan);

        if (currentPrice.compareTo(BigDecimal.ZERO) > 0 && targetPrice.compareTo(BigDecimal.ZERO) == 0) {
            throw new PMSCustomException(ResponseCode.SUBSCRIPTION_PAID_ACTIVE_CANNOT_DOWNGRADE_TO_FREE);
        }
        if (currentPrice.compareTo(BigDecimal.ZERO) > 0) {
            if (targetPrice.compareTo(currentPrice) <= 0) {
                throw new PMSCustomException(ResponseCode.SUBSCRIPTION_UPGRADE_NOT_HIGHER_TIER);
            }
            return;
        }
        if (targetPrice.compareTo(currentPrice) <= 0) {
            throw new PMSCustomException(ResponseCode.SUBSCRIPTION_UPGRADE_NOT_HIGHER_TIER);
        }
    }

    private void validatePostExpiryGraceWindow(ZonedDateTime referenceEnd, ZonedDateTime now, BigDecimal targetPrice) {
        if (referenceEnd == null) {
            return;
        }
        long daysSinceEnd = ChronoUnit.DAYS.between(referenceEnd.toLocalDate(), now.toLocalDate());
        if (daysSinceEnd > graceDaysAfterExpiry && targetPrice.compareTo(BigDecimal.ZERO) > 0) {
            throw new PMSCustomException(ResponseCode.SUBSCRIPTION_GRACE_EXPIRED_FREE_PLANS_ONLY);
        }
    }

    private static BigDecimal priceOrZero(SubscriptionPlan plan) {
        if (plan == null || plan.getPrice() == null) {
            return BigDecimal.ZERO;
        }
        return plan.getPrice();
    }

    private String normalizePlanCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private PMSRole parsePmsRoleConstant(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new PMSCustomException(ResponseCode.INVALID_ROLE);
        }
        String key = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return PMSRole.valueOf(key);
        } catch (IllegalArgumentException e) {
            throw new PMSCustomException(ResponseCode.INVALID_ROLE);
        }
    }
}
