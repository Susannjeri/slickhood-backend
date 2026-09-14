package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.UserSubscription;
import org.pms.silverocean.service.subscription.enums.SubscriptionStatus;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.subscription.enums.SubscriptionProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.pms.silverocean.service.subscription.SubscriptionSubscriberView;
import java.time.ZonedDateTime;

import java.util.List;
import java.util.Optional;

public interface UserSubscriptionRepo extends JpaRepository<UserSubscription, Long> {
    @Query("SELECT s FROM UserSubscription s WHERE s.createdOn>=:start AND s.createdOn<:end AND (:privileged=true OR s.createdBy=:userId) ORDER BY s.createdOn DESC")
    List<UserSubscription> findForReport(long userId,boolean privileged,ZonedDateTime start,ZonedDateTime end,Pageable pageable);
    List<UserSubscription> findAllByCreatedByAndStatusAndActiveTrue(long createdBy, SubscriptionStatus status);
    List<UserSubscription> findAllByCreatedByOrderByCreatedOnDesc(long createdBy);

    Optional<UserSubscription> findTopByCreatedByAndStatusAndActiveTrueOrderByStartAtDesc(long createdBy, SubscriptionStatus status);

    Optional<UserSubscription> findTopByCreatedByOrderByStartAtDesc(long createdBy);

    List<UserSubscription> findAllByCreatedByAndRoleAndStatusAndActiveTrue(
            long createdBy, PMSRole role, SubscriptionStatus status);

    Optional<UserSubscription> findTopByCreatedByAndRoleAndStatusAndActiveTrueOrderByStartAtDesc(
            long createdBy, PMSRole role, SubscriptionStatus status);

    Optional<UserSubscription> findTopByCreatedByAndRoleOrderByStartAtDesc(long createdBy, PMSRole role);

    List<UserSubscription> findAllByCreatedByAndProductKeyAndStatusAndActiveTrue(
            long createdBy, SubscriptionProduct productKey, SubscriptionStatus status);

    Optional<UserSubscription> findTopByCreatedByAndProductKeyAndStatusAndActiveTrueOrderByStartAtDesc(
            long createdBy, SubscriptionProduct productKey, SubscriptionStatus status);

    Optional<UserSubscription> findTopByCreatedByAndProductKeyOrderByStartAtDesc(
            long createdBy, SubscriptionProduct productKey);

    @Query(value = "SELECT * FROM pms_user_subscription WHERE created_by=:createdBy AND product_key=:#{#productKey.name()} " +
            "AND status=:#{#status.name()} AND active=1 ORDER BY start_at DESC LIMIT 1 FOR UPDATE", nativeQuery = true)
    Optional<UserSubscription> findActiveProductForUpdate(@Param("createdBy") long createdBy,
                                                           @Param("productKey") SubscriptionProduct productKey,
                                                           @Param("status") SubscriptionStatus status);

    List<UserSubscription> findAllByStatusAndActiveTrueAndEndAtLessThanEqual(
            SubscriptionStatus status, ZonedDateTime endAt);

    @Query(value = "SELECT new org.pms.silverocean.service.subscription.SubscriptionSubscriberView(" +
            "s.id,u.id,u.fullName,u.email,u.phoneNumber,u.accountStatus,s.role,s.productKey,s.planCode,s.status," +
            "s.startAt,s.endAt,s.autoRenew,s.createdOn) FROM UserSubscription s JOIN Users u ON u.id=s.createdBy " +
            "WHERE s.active AND u.active AND (:status IS NULL OR s.status=:status) " +
            "AND (:product IS NULL OR s.productKey=:product) AND (:search IS NULL " +
            "OR LOWER(COALESCE(u.fullName,'')) LIKE LOWER(CONCAT('%',:search,'%')) " +
            "OR LOWER(u.email) LIKE LOWER(CONCAT('%',:search,'%')) " +
            "OR LOWER(s.planCode) LIKE LOWER(CONCAT('%',:search,'%'))) ORDER BY s.createdOn DESC,s.id DESC",
            countQuery = "SELECT COUNT(s) FROM UserSubscription s JOIN Users u ON u.id=s.createdBy " +
                    "WHERE s.active AND u.active AND (:status IS NULL OR s.status=:status) " +
                    "AND (:product IS NULL OR s.productKey=:product) AND (:search IS NULL " +
                    "OR LOWER(COALESCE(u.fullName,'')) LIKE LOWER(CONCAT('%',:search,'%')) " +
                    "OR LOWER(u.email) LIKE LOWER(CONCAT('%',:search,'%')) " +
                    "OR LOWER(s.planCode) LIKE LOWER(CONCAT('%',:search,'%')))")
    Page<SubscriptionSubscriberView> findSubscriberViews(String search, SubscriptionStatus status,
                                                          SubscriptionProduct product, Pageable pageable);

    Optional<UserSubscription> findByIdAndActiveTrue(long id);
}
