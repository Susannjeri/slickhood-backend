package org.pms.silverocean.service.kyc;

public record KycAdminCaseView(long userId, String fullName, String email, KycCaseView kycCase) { }
