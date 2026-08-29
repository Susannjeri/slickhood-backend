package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

@Entity
@Table(name = "pms_help_article")
@Getter @Setter
public class HelpArticle extends BaseCreatorEntity {
    private String slug;
    private String title;
    private String category;
    @Lob @Column(columnDefinition = "TEXT")
    private String body;
    private String keywords;
    private String audienceRoles;
    private boolean published;
}
