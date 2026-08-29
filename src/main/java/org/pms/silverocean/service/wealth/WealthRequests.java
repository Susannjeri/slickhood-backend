package org.pms.silverocean.service.wealth;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class WealthRequests {
    private WealthRequests() {}

    public record AssetRequest(
            Long propertyId,
            @NotBlank @Size(max=40) String assetType,
            @NotBlank @Size(max=160) String name,
            @Size(max=120) String reference,
            @Size(max=500) String location,
            @NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency,
            @PositiveOrZero BigDecimal acquisitionCost,
            LocalDate acquisitionDate,
            @NotNull @PositiveOrZero BigDecimal currentValue,
            @NotNull LocalDate valuationDate,
            @Size(max=30) String status) {}

    public record ValuationRequest(@NotNull @PositiveOrZero BigDecimal amount,
            @NotNull LocalDate valuationDate, @NotBlank @Size(max=60) String source,
            @Size(max=1000) String notes) {}

    public record CashFlowRequest(@NotBlank @Pattern(regexp="INCOME|EXPENSE") String flowType,
            @NotBlank @Size(max=60) String category, @NotNull @Positive BigDecimal amount,
            @NotNull LocalDate entryDate, @Size(max=500) String description, boolean recurring) {}

    public record LiabilityRequest(@NotBlank @Size(max=100) String lender,
            @NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency,
            @NotNull @PositiveOrZero BigDecimal originalPrincipal,
            @NotNull @PositiveOrZero BigDecimal outstandingPrincipal,
            @PositiveOrZero BigDecimal annualInterestRate, @PositiveOrZero BigDecimal monthlyPayment,
            LocalDate startDate, LocalDate maturityDate) {}

    public record ObligationRequest(@NotBlank @Size(max=40) String obligationType,
            @NotBlank @Size(max=160) String title, LocalDate effectiveDate, LocalDate dueDate,
            LocalDate expiryDate, @PositiveOrZero BigDecimal amount,
            @Pattern(regexp="[A-Za-z]{3}") String currency,
            @Min(0) @Max(365) Integer reminderDays, @Size(max=500) String notes) {}

    public record GoalRequest(@NotBlank @Size(max=40) String goalType,
            @NotBlank @Size(max=160) String name, @NotNull @Positive BigDecimal targetAmount,
            @NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency,
            @NotNull @FutureOrPresent LocalDate targetDate) {}
}
