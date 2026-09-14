package org.pms.silverocean.service.kyc;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class KycMatrixRequests {
    private KycMatrixRequests() {}

    public record RequirementUpsert(
            @NotBlank @Pattern(regexp = "COMMON|PROVIDER_TYPE|SERVICE_CATEGORY|SOKO_CATEGORY") String scopeType,
            @NotBlank @Size(max = 120) String scopeKey,
            @NotBlank @Size(max = 180) String scopeLabel,
            @NotBlank @Pattern(regexp = "[A-Z0-9_]{2,80}") String requirementCode,
            @NotBlank @Size(max = 180) String requirementLabel,
            @NotBlank @Pattern(regexp = "MANDATORY|OPTIONAL|CONDITIONAL") String obligation,
            @NotBlank @Pattern(regexp = "INDIVIDUAL|COMPANY|BOTH") String profileScope,
            @NotBlank @Size(max = 1000) String acceptedDocumentTypes,
            @Size(max = 1000) String conditionDescription,
            @Pattern(regexp = "NON_PASSPORT_IDENTITY|PROFILE_IS|VERIFIED_DOCUMENT_PRESENT|VERIFIED_DOCUMENT_MISSING") String conditionRule,
            @Size(max = 120) String conditionValue,
            @Min(1) @Max(3650) Integer validityDays,
            @Min(0) @Max(365) Integer renewalLeadDays,
            boolean active) {}

    public record Publish(@NotBlank @Size(max = 1000) String changeSummary) {}
}
