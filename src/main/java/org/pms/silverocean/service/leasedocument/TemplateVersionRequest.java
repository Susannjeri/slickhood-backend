package org.pms.silverocean.service.leasedocument;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TemplateVersionRequest(@NotNull LeaseDocumentType documentType, @NotBlank String displayName,
        @NotBlank String bodyHtml, boolean legalReviewRequired) {}
