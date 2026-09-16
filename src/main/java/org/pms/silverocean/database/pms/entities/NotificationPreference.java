package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import org.pms.silverocean.service.notification.preferences.NotificationCategory;

@Entity
@Table(name = "pms_notification_preference", uniqueConstraints =
        @UniqueConstraint(name = "uk_notification_preference_user_category", columnNames = {"user_id", "category"}))
@Getter @Setter
public class NotificationPreference extends BaseCreatorEntity implements Auditable {
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Enumerated(EnumType.STRING)
    private NotificationCategory category;
    @Column(name = "email_enabled", nullable = false) private boolean emailEnabled;
    @Column(name = "sms_enabled", nullable = false) private boolean smsEnabled;
    @Column(name = "whatsapp_enabled", nullable = false) private boolean whatsappEnabled;
    @Version
    private long version;

    @Override public String toAuditJSON() {
        var n = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        n.put("userId", userId); n.put("category", category == null ? null : category.name());
        n.put("emailEnabled", emailEnabled); n.put("smsEnabled", smsEnabled);
        n.put("whatsappEnabled", whatsappEnabled); n.put("version", version);
        return n.toString();
    }
}
