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

@Table(name = "pms_sms", indexes = {
        @Index(name = "idx_sms_notificationId", columnList = "notificationId"),
        @Index(name = "idx_sms_receipt_due_v87", columnList = "channel,next_receipt_check_at,active")
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SMS extends BaseActiveEntity {
    private long notificationId;
    private String status;
    private String description;
    private String network;
    private double cost;
    private String currency;
    private String thirdPartyId;
    private String callBackIP;
    private LocalDateTime updatedOn;
    private String channel;
    @jakarta.persistence.Column(name = "receipt_check_attempts")
    private int receiptCheckAttempts;
    @jakarta.persistence.Column(name = "next_receipt_check_at")
    private LocalDateTime nextReceiptCheckAt;
}
