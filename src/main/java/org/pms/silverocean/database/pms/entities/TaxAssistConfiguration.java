package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "pms_tax_assist_configuration")
@Getter @Setter @NoArgsConstructor
public class TaxAssistConfiguration {
    @Id private Long id;
    @Column(nullable = false) private boolean estimatesEnabled;
    @Column(nullable = false) private boolean connectionRequestsEnabled;
    @Column(nullable = false, length = 40) private String legalNoticeVersion;
    private Long updatedBy;
    @Column(nullable = false, insertable = false, updatable = false) private ZonedDateTime updatedAt;
}
