package org.pms.silverocean.service.kyc;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record KycDocumentReviewRequest(
        @Positive long documentId,
        boolean approved,
        @Size(max = 1000) String reason,
        Map<String, String> verifiedFields,
        @Size(max = 1000) String correctionReason
) {
    public KycDocumentReviewRequest(long documentId, boolean approved, String reason) {
        this(documentId, approved, reason, Map.of(), null);
    }

    public Map<String, String> verifiedFieldsOrEmpty() {
        return verifiedFields == null ? Map.of() : verifiedFields;
    }
}
