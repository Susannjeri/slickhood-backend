package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.SubscriptionEvent;
import org.pms.silverocean.database.pms.entities.UserSubscription;
import org.pms.silverocean.service.subscription.enums.SubscriptionEventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionEventRepo extends JpaRepository<SubscriptionEvent, Long> {
    Optional<SubscriptionEvent> findTopByUserSubscriptionAndEventTypeAndActiveTrueOrderByCreatedOnDesc(
            UserSubscription subscription, SubscriptionEventType eventType);

    List<SubscriptionEvent> findAllByUserSubscriptionAndEventTypeAndActiveTrue(
            UserSubscription subscription, SubscriptionEventType eventType);
}
