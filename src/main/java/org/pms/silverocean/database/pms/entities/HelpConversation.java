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
    private long userId;
    private String activeRole;
    private String subject;
    private String status;
    private String priority;
    private Long assignedToUserId;
    private LocalDateTime lastMessageAt;
    private LocalDateTime escalatedAt;
    private LocalDateTime resolvedAt;
}
