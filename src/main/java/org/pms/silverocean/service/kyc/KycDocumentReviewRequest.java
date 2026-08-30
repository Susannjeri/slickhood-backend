package org.pms.silverocean.service.kyc;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record KycDocumentReviewRequest(
        @Positive long documentId,
        boolean approved,
        @Size(max = 1000) String reason
) { }
