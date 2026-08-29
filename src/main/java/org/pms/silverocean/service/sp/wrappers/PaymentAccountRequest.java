package org.pms.silverocean.service.sp.wrappers;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentAccountRequest(@NotNull @Positive Long paymentAccountId) {}
