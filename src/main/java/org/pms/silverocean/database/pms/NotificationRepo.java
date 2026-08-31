package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.Notification;
import org.pms.silverocean.service.notification.NotificationProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepo extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {
    @Query("SELECT n FROM Notification n WHERE n.createdOn>=:start AND n.createdOn<:end ORDER BY n.createdOn DESC")
    java.util.List<Notification> findForReport(java.time.ZonedDateTime start,java.time.ZonedDateTime end,Pageable pageable);

    @Query("SELECT n.id as notificationId, n.channel as channel, n.type as notificationType, n.recipient as recipient," +
            " n.createdOn as createdOn, n.retry as retry, n.delivered as delivered, n.retries as retryCount," +
            " s.status as status, s.description as description, s.network as network, COALESCE(s.cost, 0.0) as cost, s.currency as currency," +
            " s.callBackIP as callbackIP, n.updatedOn as lastUpdateOn  FROM Notification n LEFT JOIN SMS s ON n.id=s.notificationId WHERE " +
            " LOWER(n.recipient) LIKE CONCAT('%', :recipient, '%') OR " +
            " LOWER(n.type) LIKE CONCAT('%', :type, '%') OR " +
            " CAST(FUNCTION('DATE_FORMAT', n.createdOn, '%Y-%m-%d') AS string) LIKE CONCAT('%', :createdOn, '%')")
    Page<NotificationProjection> findByRecipientContainingOrTypeContainingOrCreatedOnContaining(Pageable pageable, String recipient, String type, String createdOn);


    @Query("SELECT n.id as notificationId, n.channel as channel, n.type as notificationType, n.recipient as recipient," +
            " n.createdOn as createdOn, n.retry as retry, n.delivered as delivered, n.retries as retryCount," +
            " s.status as status, s.description as description, s.network as network, COALESCE(s.cost, 0.0) as cost, s.currency as currency," +
            " s.callBackIP as callbackIP, n.updatedOn as lastUpdateOn  FROM Notification n LEFT JOIN SMS s ON n.id=s.notificationId")
    Page<NotificationProjection> findAllNotifications(Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.active AND n.recipient IN :recipients ORDER BY n.createdOn DESC")
    Page<Notification> findAllForRecipients(Pageable pageable, Collection<String> recipients);

    @Query("SELECT n.id FROM Notification n WHERE n.active AND n.delivered=false AND n.retry=true " +
            "AND n.channel=:channel AND n.retries<:maxRetries " +
            "AND (n.updatedOn IS NULL OR n.updatedOn<=:eligibleBefore) ORDER BY n.updatedOn,n.id")
    List<Long> findRetryCandidates(@Param("channel") String channel,
                                   @Param("eligibleBefore") LocalDateTime eligibleBefore,
                                   @Param("maxRetries") int maxRetries, Pageable pageable);

    @Modifying
    @Query("UPDATE Notification n SET n.retries=n.retries+1,n.updatedOn=:now WHERE n.id=:id AND n.active " +
            "AND n.delivered=false AND n.retry=true AND n.retries<:maxRetries " +
            "AND (n.updatedOn IS NULL OR n.updatedOn<=:eligibleBefore)")
    int claimRetry(@Param("id") long id, @Param("eligibleBefore") LocalDateTime eligibleBefore,
                   @Param("now") LocalDateTime now, @Param("maxRetries") int maxRetries);

    @Modifying
    @Query("UPDATE Notification n SET n.retry=false,n.updatedOn=:now WHERE n.id=:id AND n.delivered=false")
    int stopRetry(@Param("id") long id, @Param("now") LocalDateTime now);
}
