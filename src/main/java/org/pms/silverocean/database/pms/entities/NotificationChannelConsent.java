package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import org.pms.silverocean.service.notification.common.NotificationChannel;

import java.time.LocalDateTime;

@Entity
@Table(name = "pms_notification_channel_consent", uniqueConstraints =
        @UniqueConstraint(name = "uk_notification_consent_user_channel", columnNames = {"user_id", "channel"}))
@Getter @Setter
public class NotificationChannelConsent extends BaseCreatorEntity implements Auditable {
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Enumerated(EnumType.STRING)
    private NotificationChannel channel;
    private boolean consented;
    @Column(name = "consent_version") private String consentVersion;
    @Column(name = "consented_at") private LocalDateTime consentedAt;
    @Column(name = "revoked_at") private LocalDateTime revokedAt;
    @Version
    private long version;

    @Override public String toAuditJSON() {
        var n = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        n.put("userId", userId); n.put("channel", channel == null ? null : channel.name());
        n.put("consented", consented); n.put("consentVersion", consentVersion);
        n.put("consentedAt", consentedAt == null ? null : consentedAt.toString());
        n.put("revokedAt", revokedAt == null ? null : revokedAt.toString()); n.put("version", version);
        return n.toString();
    }
}
