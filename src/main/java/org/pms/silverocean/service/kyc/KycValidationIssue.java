package org.pms.silverocean.service.kyc;

public record KycValidationIssue(
        String field,
        String code,
        String message,
        String guidance,
        boolean blocking
) { }
