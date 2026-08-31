package org.pms.silverocean.service.subscription;

import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.CustomerWorkspaceRepo;
import org.pms.silverocean.database.pms.PlanFeatureRepo;
import org.pms.silverocean.database.pms.PlanQuotaRepo;
import org.pms.silverocean.database.pms.SubscriptionPlanRepo;
import org.pms.silverocean.database.pms.UserSubscriptionRepo;
import org.pms.silverocean.database.pms.WorkspaceMembershipRepo;
import org.pms.silverocean.database.pms.entities.SubscriptionPlan;
import org.pms.silverocean.database.pms.entities.UserSubscription;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.subscription.enums.SubscriptionProduct;
import org.pms.silverocean.service.subscription.enums.SubscriptionStatus;
import org.pms.silverocean.service.teamaccess.TeamMembershipStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.LongSupplier;

@Service
public class SubscriptionEntitlementService {
    private static final List<TeamMembershipStatus> LIVE_MEMBERSHIPS =
            List.of(TeamMembershipStatus.ACTIVE);
    private final UserDao users;
    private final UserSubscriptionRepo subscriptions;
    private final SubscriptionPlanRepo plans;
    private final PlanFeatureRepo features;
    private final PlanQuotaRepo quotas;
    private final WorkspaceMembershipRepo memberships;
    private final CustomerWorkspaceRepo workspaces;

    public SubscriptionEntitlementService(UserDao users, UserSubscriptionRepo subscriptions,
                                          SubscriptionPlanRepo plans, PlanFeatureRepo features,
                                          PlanQuotaRepo quotas, WorkspaceMembershipRepo memberships,
                                          CustomerWorkspaceRepo workspaces) {
        this.users = users; this.subscriptions = subscriptions; this.plans = plans;
        this.features = features; this.quotas = quotas; this.memberships = memberships; this.workspaces = workspaces;
    }

    @Transactional(readOnly = true)
    public UserSubscription requireProduct(SubscriptionProduct product) {
        if (internalStaff()) return null;
        long payerId = subscriptionOwner(users.getUserId());
        UserSubscription subscription = subscriptions
                .findTopByCreatedByAndProductKeyAndStatusAndActiveTrueOrderByStartAtDesc(
                        payerId, product, SubscriptionStatus.ACTIVE)
                .filter(s -> s.getEndAt() == null || s.getEndAt().isAfter(ZonedDateTime.now()))
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SUBSCRIPTION_ACCESS_REQUIRED));
        return subscription;
    }

    @Transactional(readOnly = true)
    public void requireFeature(SubscriptionProduct product, String featureKey) {
        UserSubscription subscription = requireProduct(product);
        if (subscription == null) return;
        SubscriptionPlan plan = plans.findByCodeAndActiveTrue(subscription.getPlanCode())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SUBSCRIPTION_ACCESS_REQUIRED));
        boolean included = features.findTopBySubscriptionPlanAndFeatureKeyOrderByIdDesc(plan, featureKey)
                .filter(f -> f.isActive() && f.isEnabled()).isPresent();
        if (!included) throw new PMSCustomException(ResponseCode.SUBSCRIPTION_FEATURE_NOT_INCLUDED);
    }

    /**
     * Allows a capability when it is bundled in the customer's primary plan or when the
     * customer has purchased the corresponding add-on. The decision is always made from
     * server-side subscription records; a client-supplied flag can never grant access.
     */
    @Transactional(readOnly = true)
    public void requireFeatureOrAddOn(SubscriptionProduct primaryProduct, String featureKey,
                                      SubscriptionProduct addOnProduct) {
        if (internalStaff()) return;
        long payerId = subscriptionOwner(users.getUserId());
        var primary = activeSubscription(payerId, primaryProduct);
        if (primary.isPresent()) {
            SubscriptionPlan plan = plans.findByCodeAndActiveTrue(primary.get().getPlanCode())
                    .orElseThrow(() -> new PMSCustomException(ResponseCode.SUBSCRIPTION_ACCESS_REQUIRED));
            boolean bundled = features.findTopBySubscriptionPlanAndFeatureKeyOrderByIdDesc(plan, featureKey)
                    .filter(f -> f.isActive() && f.isEnabled()).isPresent();
            if (bundled) return;
        }
        if (activeSubscription(payerId, addOnProduct).isEmpty()) {
            throw new PMSCustomException(ResponseCode.SUBSCRIPTION_FEATURE_NOT_INCLUDED);
        }
    }

    @Transactional
    public void requireAvailableQuota(SubscriptionProduct product, String metricKey,
                                      LongSupplier currentUsage, long increment) {
        if (internalStaff()) return;
        long payerId = subscriptionOwner(users.getUserId());
        UserSubscription subscription = subscriptions.findActiveProductForUpdate(
                        payerId, product, SubscriptionStatus.ACTIVE)
                .filter(s -> s.getEndAt() == null || s.getEndAt().isAfter(ZonedDateTime.now()))
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SUBSCRIPTION_ACCESS_REQUIRED));
        if (subscription == null) return;
        SubscriptionPlan plan = plans.findByCodeAndActiveTrue(subscription.getPlanCode())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SUBSCRIPTION_ACCESS_REQUIRED));
        long limit = quotas.findTopBySubscriptionPlanAndMetricKeyOrderByIdDesc(plan, metricKey)
                .filter(q -> q.isActive()).map(q -> q.getLimitValue()).orElse(0L);
        long usageAfterLock = currentUsage.getAsLong();
        if (limit >= 0 && usageAfterLock + increment > limit) {
            throw new PMSCustomException(ResponseCode.SUBSCRIPTION_LIMIT_EXCEEDED);
        }
    }

    @Transactional(readOnly = true)
    public long subscriptionOwnerUserId() { return subscriptionOwner(users.getUserId()); }

    @Transactional(readOnly = true)
    public SubscriptionProduct sessionBusinessProduct() {
        var memberWorkspace = memberships.findFirstByUserIdAndStatusInAndActiveTrueOrderByCreatedOnDesc(
                        users.getUserId(), LIVE_MEMBERSHIPS)
                .flatMap(membership -> workspaces.findById(membership.getWorkspaceId()));
        if (memberWorkspace.isPresent()) {
            return switch (memberWorkspace.get().getBusinessArea()) {
                case LANDLORD -> SubscriptionProduct.LANDLORD;
                case ESTATE_MANAGEMENT -> SubscriptionProduct.ESTATE_MANAGEMENT;
                case PROPERTY_SALE_MANAGEMENT -> SubscriptionProduct.PROPERTY_SALES;
            };
        }
        return switch (users.getActiveRole()) {
            case LANDLORD -> SubscriptionProduct.LANDLORD;
            case ESTATE_MANAGER -> SubscriptionProduct.ESTATE_MANAGEMENT;
            case SALES_AGENT -> SubscriptionProduct.PROPERTY_SALES;
            case ASSET_PORTFOLIO_MANAGER -> SubscriptionProduct.MY_WEALTH;
            case SERVICE_PROVIDER -> SubscriptionProduct.SERVICES;
            case AFFILIATE -> SubscriptionProduct.AFFILIATE;
            default -> throw new PMSCustomException(ResponseCode.SUBSCRIPTION_ACCESS_REQUIRED);
        };
    }

    /**
     * Protects shared business endpoints without blocking tenant, buyer or other
     * customer journeys that legitimately use the same controllers. Business owners
     * and active workspace members must always be backed by the workspace owner's
     * live subscription; internal SlickHood staff remain exempt.
     */
    @Transactional(readOnly = true)
    public void requireSessionBusinessProductIfApplicable() {
        if (internalStaff()) return;
        long userId = users.getUserId();
        var memberWorkspace = memberships.findFirstByUserIdAndStatusInAndActiveTrueOrderByCreatedOnDesc(
                        userId, LIVE_MEMBERSHIPS)
                .flatMap(membership -> workspaces.findById(membership.getWorkspaceId()))
                .filter(workspace -> workspace.isActive());
        if (memberWorkspace.isPresent()) {
            requireProduct(switch (memberWorkspace.get().getBusinessArea()) {
                case LANDLORD -> SubscriptionProduct.LANDLORD;
                case ESTATE_MANAGEMENT -> SubscriptionProduct.ESTATE_MANAGEMENT;
                case PROPERTY_SALE_MANAGEMENT -> SubscriptionProduct.PROPERTY_SALES;
            });
            return;
        }
        SubscriptionProduct product = switch (users.getActiveRole()) {
            case LANDLORD -> SubscriptionProduct.LANDLORD;
            case ESTATE_MANAGER -> SubscriptionProduct.ESTATE_MANAGEMENT;
            case SALES_AGENT -> SubscriptionProduct.PROPERTY_SALES;
            case ASSET_PORTFOLIO_MANAGER -> SubscriptionProduct.MY_WEALTH;
            default -> null;
        };
        if (product != null) requireProduct(product);
    }

    private long subscriptionOwner(long userId) {
        return memberships.findFirstByUserIdAndStatusInAndActiveTrueOrderByCreatedOnDesc(userId, LIVE_MEMBERSHIPS)
                .flatMap(membership -> workspaces.findById(membership.getWorkspaceId()))
                .filter(workspace -> workspace.isActive())
                .map(workspace -> workspace.getOwnerUserId())
                .orElse(userId);
    }

    private java.util.Optional<UserSubscription> activeSubscription(long payerId, SubscriptionProduct product) {
        return subscriptions.findTopByCreatedByAndProductKeyAndStatusAndActiveTrueOrderByStartAtDesc(
                        payerId, product, SubscriptionStatus.ACTIVE)
                .filter(s -> s.getEndAt() == null || s.getEndAt().isAfter(ZonedDateTime.now()));
    }

    private boolean internalStaff() {
        return users.hasRole(PMSRole.SUPER_ADMIN) || users.hasRole(PMSRole.SUPPORT)
                || users.hasRole(PMSRole.FINANCE) || users.hasRole(PMSRole.SALES_MARKETING);
    }
}
