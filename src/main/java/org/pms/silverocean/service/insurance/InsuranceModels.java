package org.pms.silverocean.service.insurance;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import org.pms.silverocean.service.account.dto.AccountPropertyDTO;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;

import java.time.LocalDate;
import java.util.List;

public final class InsuranceModels {
    private InsuranceModels() {}

    public record CompanyView(long id, String code, String name, String logoUrl, String description) {}

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
}
