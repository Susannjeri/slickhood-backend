package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.NotificationPreference;
import org.pms.silverocean.service.notification.preferences.NotificationCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepo extends JpaRepository<NotificationPreference, Long> {
    List<NotificationPreference> findAllByUserIdOrderByCategory(Long userId);
    Optional<NotificationPreference> findByUserIdAndCategory(Long userId, NotificationCategory category);
}
