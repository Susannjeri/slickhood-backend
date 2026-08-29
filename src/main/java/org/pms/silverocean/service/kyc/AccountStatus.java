package org.pms.silverocean.service.kyc;

/**
 * Customer onboarding state. The inherited {@code active} flag remains a
 * technical soft-delete/login flag; this state controls operational access.
 */
public enum AccountStatus {
    PENDING_EMAIL_VERIFICATION,
    PENDING_KYC,
    KYC_UNDER_REVIEW,
    KYC_REJECTED,
    ACTIVE,
    SUSPENDED
}
