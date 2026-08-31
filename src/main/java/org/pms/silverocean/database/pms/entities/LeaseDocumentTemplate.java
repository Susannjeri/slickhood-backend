package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import org.pms.silverocean.service.leasedocument.LeaseDocumentType;

import java.time.LocalDateTime;

@Entity
@Table(name = "pms_lease_document_template", indexes = @Index(name = "idx_document_template_type_active", columnList = "documentType, active"))
@Getter @Setter @NoArgsConstructor
public class LeaseDocumentTemplate extends BaseCreatorEntity {
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 60)
    private LeaseDocumentType documentType;
    @Column(nullable = false) private String displayName;
    @Column(nullable = false) private int version;
    @Lob @Column(nullable = false, columnDefinition = "MEDIUMTEXT") private String bodyHtml;
    private boolean legalReviewRequired;
    @Column(length = 64) private String contentSha256;
    private LocalDateTime legalReviewedAt;
    private Long legalReviewedBy;
}
