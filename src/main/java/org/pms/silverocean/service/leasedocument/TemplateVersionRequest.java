package org.pms.silverocean.service.leasedocument;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TemplateVersionRequest(@NotNull LeaseDocumentType documentType,
        @NotBlank @Size(max = 255) String displayName,
        @NotBlank @Size(max = 262_144) String bodyHtml, boolean legalReviewRequired) {}
