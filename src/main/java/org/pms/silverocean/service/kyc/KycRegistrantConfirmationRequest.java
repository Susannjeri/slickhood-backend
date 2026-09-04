package org.pms.silverocean.service.kyc;

import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

public record KycRegistrantConfirmationRequest(@NotEmpty Map<String, String> confirmedFields) { }
