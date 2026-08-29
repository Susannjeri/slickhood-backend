package org.pms.silverocean.service.sp.wrappers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompletionEvidenceRequest(@NotBlank @Size(max = 500) String evidenceReference) {}
