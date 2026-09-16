package org.pms.silverocean.service.sp.wrappers;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.pms.silverocean.service.sp.enums.DocumentType;

import java.util.Set;

public record UpdateCategoryRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 1000) String description,
        Set<DocumentType> requiredDocumentTypes,
        @Min(0) @Max(100) int requiredNumberOfReferees,
        @Min(0) long version
) {}
