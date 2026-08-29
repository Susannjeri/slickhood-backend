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
        @Index(name = "idx_sms_notificationId", columnList = "notificationId")
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
}
