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
    private String ticketNumber;
    private String guestTokenHash;
    private LocalDateTime guestExpiresAt;
    private String activeRole;
    private String subject;
    private String category;
    private String pageContext;
    private String status;
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
