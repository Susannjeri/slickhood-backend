package org.pms.silverocean.service.notification.common;

import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.database.pms.NotificationRepo;
import org.pms.silverocean.database.pms.entities.Notification;
import org.pms.silverocean.service.notification.NotificationProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Collection;
@Service
public class NotificationDao {
    private final NotificationRepo notificationRepo;

    public NotificationDao(NotificationRepo notificationRepo) {
        this.notificationRepo = notificationRepo;
    }

    public long save(Notification notification) {
        notificationRepo.save(notification);
        return notification.getId();
    }

    public Page<NotificationProjection> getNotifications(Pageable pageable, String filter) {
        if (StringUtils.isNotBlank(filter)) {
            return notificationRepo.findByRecipientContainingOrTypeContainingOrCreatedOnContaining(pageable, filter.toLowerCase(), filter.toLowerCase(), filter.toLowerCase());
        }
        return notificationRepo.findAllNotifications(pageable);
    }

    public Optional<Notification> findById(long id) {
        return notificationRepo.findById(id);
    }

    public Page<Notification> getNotificationsForRecipients(Pageable pageable, Collection<String> recipients) {
        return notificationRepo.findAllForRecipients(pageable, recipients);
    }

}
