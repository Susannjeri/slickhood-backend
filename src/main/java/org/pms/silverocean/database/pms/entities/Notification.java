package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
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
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Notification extends BaseActiveEntity {
    private byte[] message;
    private String recipient;
    private String channel;
    private String type;

    private boolean delivered;
    private boolean retry;
    private int retries;
    private LocalDateTime updatedOn;
}
