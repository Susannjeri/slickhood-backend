package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.UserSubscription;
import org.pms.silverocean.service.subscription.enums.SubscriptionStatus;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
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
}
