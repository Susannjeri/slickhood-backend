package org.pms.silverocean.service.estate;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ServiceChargeRequest(@Positive long ownershipId,@NotNull @Positive @Digits(integer=12,fraction=2) BigDecimal amount,
 @NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency,@NotNull @FutureOrPresent LocalDate dueDate,@NotBlank @Size(max=255) String description){}
