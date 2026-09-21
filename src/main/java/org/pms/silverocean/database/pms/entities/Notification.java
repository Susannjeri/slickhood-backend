package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseActiveEntity;

import java.time.LocalDateTime;

@Table(name = "pms_notification", indexes = {
        @Index(name = "idx_notification_recipient", columnList = "recipient"),
        @Index(name = "idx_notification_channel", columnList = "channel"),
        @Index(name = "idx_notification_delivered", columnList = "delivered"),
        @Index(name = "idx_notification_business_event_v87", columnList = "business_event_key,recipient,channel"),
        @Index(name = "uk_notification_delivery_key_v87", columnList = "delivery_key", unique = true),
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Notification extends BaseActiveEntity {
    @Lob
    @Column(columnDefinition = "MEDIUMBLOB")
    private byte[] message;
    private String recipient;
    private String channel;
    private String type;

    private boolean delivered;
    @Column(name = "provider_status", length = 32)
    private String providerStatus;
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
    @Column(name = "failed_at")
    private LocalDateTime failedAt;
    private boolean retry;
    private int retries;
    private LocalDateTime updatedOn;
    private LocalDateTime viewedOn;
    @Column(name = "business_event_key", length = 64)
    private String businessEventKey;
    @Column(name = "delivery_key", length = 64)
    private String deliveryKey;
    @Column(name = "action_path", length = 500)
    private String actionPath;
}
