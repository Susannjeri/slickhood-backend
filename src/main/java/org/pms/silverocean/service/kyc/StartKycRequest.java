package org.pms.silverocean.service.kyc;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record StartKycRequest(@AssertTrue(message = "KYC data processing consent is required") boolean consent,
                              @NotBlank String consentVersion) { }
