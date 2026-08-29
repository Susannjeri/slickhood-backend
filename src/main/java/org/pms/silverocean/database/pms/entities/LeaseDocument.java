package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import org.pms.silverocean.service.leasedocument.LeaseDocumentStatus;
import org.pms.silverocean.service.leasedocument.LeaseDocumentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pms_lease_document", indexes = {
        @Index(name = "idx_document_lease", columnList = "leaseId, active"),
        @Index(name = "idx_document_property", columnList = "propertyId, active"),
        @Index(name = "idx_document_parties", columnList = "issuerUserId, recipientUserId, active")})
@Getter @Setter @NoArgsConstructor
public class LeaseDocument extends BaseCreatorEntity {
    private Long leaseId;
    private Long propertyId;
    private Long unitId;
    @Column(nullable = false) private long templateId;
    @Column(nullable = false) private int templateVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 60) private LeaseDocumentType documentType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private LeaseDocumentStatus status;
    @Column(nullable = false) private String name;
    @Lob @Column(nullable = false, columnDefinition = "LONGTEXT") private String renderedHtml;
    private long issuerUserId;
    private long recipientUserId;
    private LocalDate effectiveDate;
    private LocalDate responseDueDate;
    private BigDecimal amount;
    private String currency;
    @Column(length = 1000) private String reason;
    private String deliveryChannel;
    private boolean legalReviewRequired;
    private LocalDateTime issuedAt;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime issuerSignedAt;
    private LocalDateTime recipientSignedAt;
}
