package org.pms.silverocean.service.payment.platforms.mpesa.wrappers;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Bank callback for an M-Pesa Paybill settlement. The M-Pesa receipt remains
 * the canonical settlement ID so a direct and bank callback cannot pay twice;
 * the bank reference remains in the audited canonical callback payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MPesaBankCallbackDTO(
        @NotBlank @JsonAlias({"mpesaRef", "mpesaTransactionId", "TransID"}) String mpesaReference,
        @NotBlank @JsonAlias({"bankRef", "bankTransactionId", "BankTransID"}) String bankReference,
        @NotBlank @JsonAlias({"invoiceRef", "invoiceNumber", "BillRefNumber"}) String invoiceReference,
        @NotNull @Positive @JsonAlias({"TransAmount", "paymentAmount"}) BigDecimal amount,
        @NotBlank @JsonAlias({"TransTime", "paymentTime"}) String transactionTime,
        @JsonAlias({"bankAccount", "BusinessShortCode"}) String bankAccountNumber,
        @JsonAlias({"MSISDN", "phoneNumber"}) String customerPhoneNumber,
        String customerName) {

    public MPesaPaymentDTO toMPesaPaymentDTO() {
        return new MPesaPaymentDTO(
                "Bank M-Pesa settlement", mpesaReference.trim(), transactionTime,
                amount.toPlainString(), bankAccountNumber, invoiceReference,
                invoiceReference, null, bankReference, customerPhoneNumber,
                customerName, null, null);
    }
}
