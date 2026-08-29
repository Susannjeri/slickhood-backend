package org.pms.silverocean.service.estate;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ServiceChargeRequest(@Positive long ownershipId,@NotNull @Positive BigDecimal amount,
 @NotBlank String currency,@NotNull @FutureOrPresent LocalDate dueDate,@NotBlank @Size(max=255) String description){}
