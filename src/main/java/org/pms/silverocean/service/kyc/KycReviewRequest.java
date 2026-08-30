package org.pms.silverocean.service.kyc;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record KycReviewRequest(
        @NotNull KycStatus decision,
        @Size(max = 1000) String notes,
        List<@Valid KycDocumentReviewRequest> documents
) {
    public KycReviewRequest(KycStatus decision, String notes) {
        this(decision, notes, List.of());
    }

    public List<KycDocumentReviewRequest> documentsOrEmpty() {
        return documents == null ? List.of() : documents;
    }
}
