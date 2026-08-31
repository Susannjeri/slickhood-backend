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
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
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

    public List<Long> findRetryCandidates(String channel, LocalDateTime eligibleBefore, int maxRetries, int batchSize) {
        return notificationRepo.findRetryCandidates(channel, eligibleBefore, maxRetries, PageRequest.of(0, batchSize));
    }

    @Transactional
    public boolean claimRetry(long id, LocalDateTime eligibleBefore, LocalDateTime now, int maxRetries) {
        return notificationRepo.claimRetry(id, eligibleBefore, now, maxRetries) == 1;
    }

    @Transactional
    public void stopRetry(long id) {
        notificationRepo.stopRetry(id, LocalDateTime.now());
    }

}
