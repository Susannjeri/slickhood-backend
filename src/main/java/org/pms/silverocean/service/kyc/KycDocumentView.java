package org.pms.silverocean.service.kyc;

import org.pms.silverocean.database.pms.entities.KycDocument;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record KycDocumentView(long id, String documentType, String originalFileName, String contentType,
                              String status, String qualityStatus,
                              Double qualityScore, Double ocrConfidence, Map<String, String> extractedFields,
                              List<KycValidationIssue> validationIssues,
                              String rejectionReason, ZonedDateTime uploadedAt, String downloadUrl) {
    static KycDocumentView from(KycDocument document, Map<String, String> extractedFields, String downloadUrl) {
        return new KycDocumentView(document.getId(), document.getDocumentType(), document.getOriginalFileName(),
                document.getContentType(), document.getStatus(),
                document.getQualityStatus(), document.getQualityScore(), document.getOcrConfidence(),
                extractedFields, issues(extractedFields, document.getRejectionReason(),
                        document.getOcrConfidence(), document.getStatus()),
                document.getRejectionReason(), document.getCreatedOn(), downloadUrl);
    }

    static List<KycValidationIssue> issues(Map<String, String> fields, String rejectionReason,
                                           Double confidence, String status) {
        List<KycValidationIssue> issues = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String warnings = fields == null ? null : fields.get("_validationWarnings");
        if (warnings != null && !warnings.isBlank()) {
            for (String warning : warnings.split(";")) addWarning(issues, seen, warning.trim());
        }
        if (issues.isEmpty() && rejectionReason != null && !rejectionReason.isBlank()) {
            add(issues, seen, "document", "DOCUMENT_REJECTED", rejectionReason.trim(),
                    "Replace this file with a clear, complete original document.");
        }
        if (issues.isEmpty() && DocumentStatus.REJECTED.name().equals(status)
                && confidence != null && confidence < 75.0) {
            add(issues, seen, "document", "LOW_OCR_CONFIDENCE",
                    "The document text could not be read confidently.",
                    "Retake the image in good light, keep all corners visible and avoid glare or blur.");
        }
        return List.copyOf(issues);
    }

    private static void addWarning(List<KycValidationIssue> issues, Set<String> seen, String warning) {
        if (warning.isBlank()) return;
        String normalized = warning.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("could not confidently extract:")) {
            String missing = warning.substring(warning.indexOf(':') + 1);
            for (String item : missing.split(",")) {
                String fieldName = item.trim().toLowerCase(Locale.ROOT);
                if (fieldName.contains("kra") || fieldName.contains("tax")) {
                    add(issues, seen, "taxPin", "TAX_PIN_UNREADABLE",
                            "The KRA PIN is missing, unreadable or has an invalid format.",
                            "Upload the original KRA PIN certificate with the full PIN visible.");
                } else if (fieldName.contains("document number") || fieldName.contains("identity")) {
                    add(issues, seen, "documentNumber", "DOCUMENT_NUMBER_UNREADABLE",
                            "The identity document number is missing or unreadable.",
                            "Upload the original identity document with the complete number in focus.");
                } else if (fieldName.contains("name")) {
                    add(issues, seen, "fullName", "NAME_UNREADABLE",
                            "The holder's name is missing or unreadable.",
                            "Upload the original document with the full name clearly visible.");
                } else {
                    add(issues, seen, "document", "FIELD_UNREADABLE",
                            "Required information could not be read: " + item.trim() + ".",
                            "Upload a clearer, complete original document.");
                }
            }
            return;
        }
        if (normalized.contains("name on the document") && normalized.contains("account name")) {
            add(issues, seen, "fullName", "NAME_MISMATCH",
                    "The name on this document does not match the account holder's name.",
                    "Upload the account holder's document, or correct the account profile before trying again.");
            return;
        }
        if (normalized.contains("document number conflicts")) {
            add(issues, seen, "documentNumber", "DOCUMENT_NUMBER_CONFLICT",
                    "This number conflicts with another identity document in the same KYC case.",
                    "Use documents belonging to the same person and ensure both sides are from the same ID.");
            return;
        }
        add(issues, seen, "document", "OCR_VALIDATION_FAILED", warning,
                "Replace this file with a clear, complete original document.");
    }

    private static void add(List<KycValidationIssue> issues, Set<String> seen, String field, String code,
                            String message, String guidance) {
        if (seen.add(field + "|" + code + "|" + message)) {
            issues.add(new KycValidationIssue(field, code, message, guidance, true));
        }
    }
}
