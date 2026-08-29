package org.pms.silverocean.service.kyc;

import org.pms.silverocean.database.pms.entities.KycDocument;

import java.time.ZonedDateTime;
import java.util.Map;

public record KycDocumentView(long id, String documentType, String originalFileName, String contentType,
                              String status, String qualityStatus,
                              Double qualityScore, Double ocrConfidence, Map<String, String> extractedFields,
                              String rejectionReason, ZonedDateTime uploadedAt, String downloadUrl) {
    static KycDocumentView from(KycDocument document, Map<String, String> extractedFields, String downloadUrl) {
        return new KycDocumentView(document.getId(), document.getDocumentType(), document.getOriginalFileName(),
                document.getContentType(), document.getStatus(),
                document.getQualityStatus(), document.getQualityScore(), document.getOcrConfidence(),
                extractedFields, document.getRejectionReason(), document.getCreatedOn(), downloadUrl);
    }
}
