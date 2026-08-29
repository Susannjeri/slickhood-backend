package org.pms.silverocean.service.sp.wrappers;

import jakarta.validation.constraints.NotBlank;
import org.pms.silverocean.service.sp.enums.DocumentType;

import java.util.Set;

public record CreateCategoryRequest(
    @NotBlank String name,
    String description,
    Set<DocumentType> requiredDocumentTypes,
    int requiredNumberOfReferees
) {}
