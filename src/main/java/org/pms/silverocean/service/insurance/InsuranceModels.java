package org.pms.silverocean.service.insurance;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import org.pms.silverocean.service.account.dto.AccountPropertyDTO;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

public final class InsuranceModels {
    private InsuranceModels() {}

    public record CompanyView(long id, String code, String name, String logoUrl, String description, boolean active) {}

    public record CompanyAdminView(long id, String code, String name, String logoUrl, String description,
            String quotationEmail, String claimsEmail, String renewalsEmail, boolean active) {}

    public record CompanyCreateRequest(
            @NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{1,49}") String code,
            @NotBlank @Size(max=160) String name, @Size(max=800) String logoUrl,
            @Size(max=1000) String description, @Email String quotationEmail,
            @Email String claimsEmail, @Email String renewalsEmail) {}

    public record CompanyUpdateRequest(@NotBlank @Size(max=160) String name,
            @Size(max=800) String logoUrl, @Size(max=1000) String description,
            @Email String quotationEmail, @Email String claimsEmail,
            @Email String renewalsEmail, @NotNull Boolean active) {}

    public record CompanyEmailConfigurationRequest(@Email String quotationEmail,
            @Email String claimsEmail, @Email String renewalsEmail) {}

    public record CompanyEmailConfigurationView(long id, String code, String name,
            String quotationEmail, String claimsEmail, String renewalsEmail, boolean readyForQuotations) {}

    public record PaymentConfigurationRequest(@Positive long paymentAccountId, @NotBlank String label,
            @NotBlank String instructions, String referenceTemplate,
            @NotNull @FutureOrPresent LocalDate effectiveFrom) {}

    public record PaymentConfigurationView(long id, String companyCode, String companyName,
            long paymentAccountId, String accountName, PaymentChannel channel, String label,
            String instructions, String referenceTemplate, int version, LocalDate effectiveFrom,
            LocalDate effectiveTo, boolean active, boolean accountVerified,
            List<AccountPropertyDTO> paymentDetails) {}

    public record InsurerEmailRequest(@NotBlank String companyCode, @NotBlank String caseReference,
            @NotBlank @Pattern(regexp = "QUOTATION_REQUEST|CLAIM_NOTIFICATION|RENEWAL_REQUEST") String messageType,
            @NotBlank String subject, @NotBlank String body) {}

    public record InsurerEmailResponse(@NotBlank String correlationId, @Email @NotBlank String fromAddress,
            @NotBlank String subject, @NotBlank String body, String externalMessageId) {}

    public record EmailExchangeView(long id, String companyCode, String companyName, String caseReference,
            String correlationId, String messageType, String direction, String status,
            String senderAddress, String recipientAddress, String subject, String bodyHash,
            String externalMessageId, String inReplyTo, java.time.LocalDateTime sentAt,
            java.time.LocalDateTime receivedAt, String lastError) {}

    public record ProductView(String code,String name,String description,List<String> subjectTypes) {}
    public record AgencyView(String code,String name,String supportEmail,String supportPhone,String logoUrl) {}

    public record CaseRequest(
            @NotBlank @Pattern(regexp="MOTOR|DOMESTIC|FIRE_ALLIED|WIBA_EL|ALL_RISKS|MEDICAL|MARINE_CARGO|TRAVEL") String productCode,
            @NotBlank @Size(max=160) String fullName,@Email @NotBlank String email,
            @NotBlank @Size(max=40) String phone,@NotBlank @Pattern(regexp="PERSON|VEHICLE|PROPERTY|BUSINESS|EMPLOYEES|GOODS|TRIP|VALUABLES") String subjectType,
            @NotBlank @Size(max=1000) String subjectDescription,@DecimalMin("0.01") BigDecimal sumInsured,
            @NotBlank @Pattern(regexp="[A-Z]{3}") String currency,LocalDate coverStartDate,
            @Size(max=8000) String riskDetails,@NotNull Boolean consent) {}
    public record CaseStatusRequest(@NotBlank @Pattern(regexp="ADVISER_ASSIGNED|INFORMATION_REQUIRED|WITHDRAWN") String status,@Size(max=1000) String note) {}
    public record AssignmentRequest(@Positive long adviserUserId) {}
    public record QuoteRequest(@Positive long companyId,@Size(max=80) String quoteNumber,
            @NotBlank @Pattern(regexp="[A-Z]{3}") String currency,@NotNull @DecimalMin("0.00") BigDecimal basePremium,
            @NotNull @DecimalMin("0.00") BigDecimal taxesLevies,@NotNull @DecimalMin("0.01") BigDecimal totalPremium,
            @Size(max=1000) String excessDetails,@NotBlank @Size(max=12000) String coverageSummary,
            @Size(max=12000) String exclusions,@NotNull @FutureOrPresent LocalDate validUntil) {}
    public record SelectQuoteRequest(@Positive long quoteId) {}
    public record PaymentRequest(@Positive Long paymentConfigurationId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,@NotBlank @Pattern(regexp="[A-Z]{3}") String currency,
            @NotBlank @Size(max=120) String paymentReference,@NotNull LocalDateTime paidAt) {}
    public record PaymentDecisionRequest(@NotBlank @Pattern(regexp="VERIFIED|REJECTED") String status,@Size(max=500) String reason) {}
    public record RemittanceRequest(@NotBlank @Size(max=120) String reference) {}
    public record PolicyRequest(@NotBlank @Size(max=120) String policyNumber,@NotNull LocalDate startDate,@NotNull LocalDate endDate) {}
    public record ClaimRequest(@Positive long policyId,@NotNull LocalDateTime incidentAt,@Size(max=300) String incidentLocation,
            @NotBlank @Size(max=12000) String description,@DecimalMin("0.00") BigDecimal estimatedAmount) {}
    public record ClaimStatusRequest(@NotBlank @Pattern(regexp="ACKNOWLEDGED|DOCS_REQUIRED|SENT_TO_INSURER|ASSESSED|APPROVED|DECLINED|SETTLED|CLOSED") String status,
            @Size(max=120) String insurerReference,@Size(max=8000) String note) {}
    public record RenewalRequest(@NotBlank @Pattern(regexp="CONTACTED|RENEWAL_QUOTED|ACCEPTED|PAID|RENEWED|LAPSED") String status) {}

    public record QuoteView(long id,long companyId,String companyCode,String companyName,String quoteNumber,String status,
            String currency,BigDecimal basePremium,BigDecimal taxesLevies,BigDecimal totalPremium,String excessDetails,
            String coverageSummary,String exclusions,LocalDate validUntil) {}
    public record PaymentView(long id,long quoteId,Long paymentConfigurationId,BigDecimal amount,String currency,String paymentReference,LocalDateTime paidAt,
            String status,String rejectionReason,String remittanceReference,LocalDateTime remittedAt,boolean proofAvailable,String proofContentType) {}
    public record CaseView(long id,String reference,String productCode,String status,String fullName,String email,String phone,
            String subjectType,String subjectDescription,BigDecimal sumInsured,String currency,LocalDate coverStartDate,String riskDetails,
            Long assignedAdviserId,LocalDateTime submittedAt,Long selectedQuoteId,List<QuoteView> quotes,List<PaymentView> payments) {}
    public record PolicyView(long id,long caseId,String policyNumber,String companyName,String productCode,String status,
            LocalDate startDate,LocalDate endDate,String renewalStatus) {}
    public record ClaimView(long id,long policyId,String policyNumber,String reference,String status,LocalDateTime incidentAt,
            String incidentLocation,String description,BigDecimal estimatedAmount,String insurerReference,String resolutionNotes) {}
    public record DocumentView(long id,Long caseId,Long policyId,Long claimId,String category,String displayName,String contentType,
            long fileSize,String checksumSha256,String downloadUrl,int versionNumber) {}
    public record OperationsSummary(long openCases,long unassignedCases,long paymentsAwaitingVerification,long openClaims,long renewalsDue) {}
    public record StaffView(long id,String fullName,String email,String roleName) {}
}
