package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

@Entity
@Table(name = "pms_help_message")
@Getter @Setter
public class HelpMessage extends BaseCreatorEntity {
    private long conversationId;
    private String senderType;
    @Lob @Column(columnDefinition = "TEXT")
    private String content;
    private String model;
    private String providerResponseId;
    private String sourceArticleIds;
}
