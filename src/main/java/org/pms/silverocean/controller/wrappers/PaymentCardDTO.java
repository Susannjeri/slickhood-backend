package org.pms.silverocean.controller.wrappers;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.pms.silverocean.config.ValidCardExpiry;

public record PaymentCardDTO(
        @NotBlank(message = "Card Name cannot be empty")
        String name,
        @NotBlank(message = "Card account number cannot be empty")
        @Pattern(regexp = "\\d+", message = "Card Account number can only have digits")
        @Size(min = 13, max = 19, message = "Card number must be between 13 and 19 digits")
        String accountNumber,
        @ValidCardExpiry
        String expiry,
        @NotEmpty(message = "CVV cannot be empty") @Min(value = 100, message = "Invalid cvv length") @Max(value = 999, message = "Invalid cvv length")
        Integer cvv) {
}
