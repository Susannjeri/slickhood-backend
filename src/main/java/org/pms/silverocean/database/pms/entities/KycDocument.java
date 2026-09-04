package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseActiveEntity;

import java.time.ZonedDateTime;

@Entity
@Table(name = "pms_kyc_document", indexes = {
        @Index(name = "idx_kyc_document_case", columnList = "caseId,active"),
        @Index(name = "idx_kyc_document_hash", columnList = "userId,sha256")
})
@Getter @Setter
public class KycDocument extends BaseActiveEntity {
    private long caseId;
    private long userId;
    private String documentType;
    private String originalFileName;
    private String contentType;
    private String fileRef;
    private long fileSize;
    private String sha256;
    private Integer width;
    private Integer height;
    private Double qualityScore;
    private String qualityStatus;
    private String status;
    private String ocrProvider;
    private Double ocrConfidence;
    @Lob private byte[] encryptedExtractedData;
    /** Registrant-confirmed key fields are retained separately from immutable OCR evidence. */
    @Lob private byte[] encryptedRegistrantConfirmedData;
    private ZonedDateTime registrantConfirmedAt;
    private Long registrantConfirmedBy;
    /** Reviewer-entered values are stored separately so the original OCR evidence remains immutable. */
    @Lob private byte[] encryptedReviewerVerifiedData;
    @Column(columnDefinition = "TEXT")
    private String reviewerCorrectionReason;
    @Column(columnDefinition = "TEXT")
    private String rejectionReason;
    private Long supersedesDocumentId;
    private ZonedDateTime reviewedAt;
    private Long reviewedBy;
    private int versionNo;
    private ZonedDateTime issuedAt;
    private ZonedDateTime expiresAt;
    private ZonedDateTime reverificationDueAt;
    private String maintenanceReason;
}
