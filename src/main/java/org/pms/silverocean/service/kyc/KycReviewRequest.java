package org.pms.silverocean.service.kyc;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KycReviewRequest(@NotNull KycStatus decision, @Size(max = 1000) String notes) { }
