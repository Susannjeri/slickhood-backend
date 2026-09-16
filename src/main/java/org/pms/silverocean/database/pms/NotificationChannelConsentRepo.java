package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.NotificationChannelConsent;
import org.pms.silverocean.service.notification.common.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationChannelConsentRepo extends JpaRepository<NotificationChannelConsent, Long> {
    Optional<NotificationChannelConsent> findByUserIdAndChannel(Long userId, NotificationChannel channel);
}
