package org.pms.silverocean.service.tax;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.Set;

public final class TaxModels {
    private TaxModels() {}

    public record MriEstimateRequest(
            @NotNull YearMonth period,
            boolean kenyaResidentialProperty,
            boolean residentTaxpayer,
            boolean electedOutOfMri,
            @NotNull @DecimalMin("0.00") @Digits(integer=17, fraction=2) BigDecimal projectedAnnualGrossRent,
            @NotNull @DecimalMin("0.00") @Digits(integer=17, fraction=2) BigDecimal grossRentReceived,
            @NotNull @DecimalMin("0.00") @Digits(integer=17, fraction=2) BigDecimal withholdingCredits) {}

    public record CgtEstimateRequest(
            @NotNull LocalDate transferDate,
            @NotNull @DecimalMin("0.00") @Digits(integer=17, fraction=2) BigDecimal transferValue,
            @NotNull @DecimalMin("0.00") @Digits(integer=17, fraction=2) BigDecimal transferCosts,
            @NotNull @DecimalMin("0.00") @Digits(integer=17, fraction=2) BigDecimal acquisitionCost,
            @NotNull @DecimalMin("0.00") @Digits(integer=17, fraction=2) BigDecimal acquisitionCosts,
            @NotNull @DecimalMin("0.00") @Digits(integer=17, fraction=2) BigDecimal enhancementCosts,
            boolean propertyDealer,
            boolean potentialExemption,
            @Size(max = 500) String exemptionReason) {}

    public record CalculationView(Long id, String calculationType, String taxPeriod, String currency,
                                  BigDecimal grossAmount, BigDecimal taxableAmount, BigDecimal estimatedTax,
                                  BigDecimal creditAmount, BigDecimal estimatedPayable, String outcome,
                                  String explanation, LocalDate dueDate, RuleView rule, ZonedDateTime createdOn) {}

    public record RuleView(Long id, String ruleCode, int version, LocalDate effectiveFrom, LocalDate effectiveTo,
                           BigDecimal rate, BigDecimal lowerThreshold, BigDecimal upperThreshold, String currency,
                           String sourceUrl, String sourceNote, boolean active) {}

    public record RuleRequest(
            @NotBlank @Pattern(regexp = "KENYA_MRI|KENYA_CGT_PROPERTY") String ruleCode,
            @Positive int version,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @NotNull @DecimalMin("0.00000001") @DecimalMax("1.00000000") BigDecimal rate,
            @DecimalMin("0.00") BigDecimal lowerThreshold,
            @DecimalMin("0.00") BigDecimal upperThreshold,
            @NotBlank @Pattern(regexp = "https://.*") @Size(max = 500) String sourceUrl,
            @NotBlank @Size(max = 1000) String sourceNote) {}

    public record RuleCloseRequest(@NotNull LocalDate effectiveTo) {}

    public record ConnectionRequest(
            @NotBlank @Pattern(regexp = "ETIMS|GAVACONNECT") String provider,
            @NotBlank @Pattern(regexp = "[A-Za-z][0-9]{9}[A-Za-z]") String taxpayerPin,
            @NotEmpty Set<@Pattern(regexp = "ISSUE_TAX_INVOICES|QUERY_INVOICE_STATUS|FILE_TAX_RETURN|CREATE_PAYMENT_REFERENCE|QUERY_SUBMISSION_STATUS") String> requestedScopes,
            @AssertTrue(message = "Explicit consent is required") boolean consentAccepted) {}

    public record ConnectionView(Long id, String provider, String taxpayerPinMasked, Set<String> requestedScopes,
                                 String consentVersion, ZonedDateTime consentedAt, String status,
                                 String environment, String reviewNote, ZonedDateTime createdOn) {}

    public record ConnectionReview(@NotBlank @Pattern(regexp = "AWAITING_KRA_APPROVAL|SANDBOX_READY|SUSPENDED|REJECTED") String status,
                                   @NotBlank @Size(max = 1000) String reviewNote) {}
}
