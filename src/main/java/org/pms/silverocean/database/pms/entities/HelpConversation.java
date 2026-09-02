package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "pms_help_conversation")
@Getter @Setter
public class HelpConversation extends BaseCreatorEntity {
    private Long userId;
    @Column(length = 24, nullable = false)
    private String ticketNumber;
    @Column(columnDefinition = "CHAR(64)")
    private String guestTokenHash;
    private LocalDateTime guestExpiresAt;
    @Column(length = 60, nullable = false)
    private String activeRole;
    @Column(length = 180, nullable = false)
    private String subject;
    @Column(length = 60, nullable = false)
    private String category;
    @Column(length = 255)
    private String pageContext;
    @Column(length = 30, nullable = false)
    private String status;
    @Column(length = 20, nullable = false)
    private String priority;
    private int priorityRank;
    private Long assignedToUserId;
    private LocalDateTime lastMessageAt;
    private LocalDateTime waitingSince;
    private LocalDateTime slaDueAt;
    private LocalDateTime slaBreachedAt;
    private LocalDateTime firstResponseAt;
    private LocalDateTime escalatedAt;
    private LocalDateTime resolvedAt;
    private int customerUnreadCount;
    private int agentUnreadCount;

    @Version
    private long version;
}
