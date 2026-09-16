package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.database.pms.entities.base.BaseIDEntity;
import org.pms.silverocean.service.payment.platforms.mpesa.TransactionCategory;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.MPesaPaymentDTO;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.MPesaSTKPushRequest;
import org.pms.silverocean.service.payment.platforms.pesalink.wrappers.IPNCallbackDTO;
import org.pms.silverocean.service.payment.platforms.pesalink.wrappers.PesalinkValidatePaymentRequestDTO;
import org.pms.silverocean.service.payment.wrappers.ManualPaymentDTO;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;

import java.time.LocalDateTime;
import java.math.BigDecimal;

@Table(name = "pms_payment", indexes = {
        @Index(name = "idx_payment_customer_account_number", columnList = "customerAccountNumber"),
        @Index(name = "idx_payment_receiving_account_number", columnList = "receivingAccountNumber"),
        @Index(name = "idx_payment_transid", columnList = "thirdPartyTransId"),
        @Index(name = "idx_payment_bill_reference", columnList = "billReference"),
        @Index(name = "idx_payment_category_status", columnList = "category, status"),
        @Index(name = "idx_filter", columnList = "billReference, category, status")
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PMSPayment extends BaseIDEntity {
    private Double amount;
    @Column(name = "amount_decimal", precision = 19, scale = 2)
    private BigDecimal amountDecimal;
    @Column(name = "currency_code", length = 3)
    private String currencyCode;
    private String customerAccountNumber;
    private String receivingAccountNumber;
    private String billReference;
    private String channel;
    private String category;
    private String customerName;

    private String thirdPartyTransId;
    /** Canonical provider settlement receipt. Kept separate from request IDs and globally de-duplicated per rail. */
    private String providerReceipt;
    private String status;
    private String statusDesc;
    private long payToUserId;
    private String sourceIp;
    private boolean flag = false;
    private int verificationRetries = 0;
    private LocalDateTime updatedOn;
    private boolean inProgress = true;
    private Long accountId;

    public PMSPayment(MPesaPaymentDTO mpesaPaymentDTO, TransactionCategory transactionCategory) {
        this.thirdPartyTransId = mpesaPaymentDTO.transId();
        if (StringUtils.isNotBlank(mpesaPaymentDTO.transAmount())) setMoneyAmount(new BigDecimal(mpesaPaymentDTO.transAmount()));
        this.billReference = mpesaPaymentDTO.billRefNumber();
        this.customerAccountNumber = mpesaPaymentDTO.msisdn();
        this.customerName = String.format("%s %s %s", mpesaPaymentDTO.firstName(), mpesaPaymentDTO.middleName(), mpesaPaymentDTO.lastName());
        this.channel = PaymentChannel.MPESA.getName();
        this.category = transactionCategory.name();
        this.receivingAccountNumber = mpesaPaymentDTO.businessShortCode();
    }

    public PMSPayment(MPesaSTKPushRequest mpesaSTKPushRequest, long accountId) {
        setMoneyAmount(new BigDecimal(mpesaSTKPushRequest.amount()));
        this.customerAccountNumber = mpesaSTKPushRequest.phoneNumber();
        this.billReference = mpesaSTKPushRequest.accountReference();
        this.channel = PaymentChannel.MPESA.getName();
        this.category = TransactionCategory.STK.name();
        this.receivingAccountNumber = mpesaSTKPushRequest.businessShortCode();
        this.accountId = accountId;
    }

    public PMSPayment(PMSInvoice pmsInvoice, String customerName, long accountId) {
        setMoneyAmount(pmsInvoice.moneyPendingAmount());
        this.currencyCode = org.pms.silverocean.service.payment.money.MonetaryPolicy.currency(pmsInvoice.getCurrency());
        this.customerAccountNumber = pmsInvoice.getCustomerEmail();
        this.billReference = pmsInvoice.getRef();
        this.channel = PaymentChannel.FLUTTER_WAVE.getName();
        this.category = TransactionCategory.CARD_PAYMENT.name();
        this.receivingAccountNumber = "collection";
        this.payToUserId = pmsInvoice.getPayToUserId();
        this.customerName = customerName;
        this.accountId = accountId;
    }

    public PMSPayment(ManualPaymentDTO manualPaymentDTO) {
        this.billReference = manualPaymentDTO.invoiceRef();
        setMoneyAmount(manualPaymentDTO.amount());
        this.category = TransactionCategory.MANUAL_RECORD.name();
        this.channel = manualPaymentDTO.channel();
        this.thirdPartyTransId = manualPaymentDTO.transId();
        this.status = "success";
        this.inProgress = false;
    }

    public PMSPayment(PesalinkValidatePaymentRequestDTO pesalinkValidatePaymentRequestDTO, TransactionCategory transactionCategory) {
        this.thirdPartyTransId = pesalinkValidatePaymentRequestDTO.requestId();
        setMoneyAmount(pesalinkValidatePaymentRequestDTO.amount());
        this.billReference = pesalinkValidatePaymentRequestDTO.billRef();
        this.channel = PaymentChannel.PESA_LINK.getName();
        this.category = transactionCategory.name();
    }

    public PMSPayment(IPNCallbackDTO ipnCallbackDTO, TransactionCategory transactionCategory) {
        this.thirdPartyTransId = ipnCallbackDTO.rrn();
        setMoneyAmount(ipnCallbackDTO.amount());
        this.billReference = ipnCallbackDTO.billReference();
        this.channel = PaymentChannel.PESA_LINK.getName();
        this.category = transactionCategory.name();
    }

    public boolean isCompletedSuccessfully() {
        return inProgress ? false : TransactionCategory.valueOf(category).getSuccessString().equals(status);
    }

    public BigDecimal moneyAmount() {
        if (amountDecimal != null) return org.pms.silverocean.service.payment.money.MonetaryPolicy.amount(amountDecimal);
        return amount == null ? null : org.pms.silverocean.service.payment.money.MonetaryPolicy.amount(amount);
    }

    public void setMoneyAmount(BigDecimal value) {
        amountDecimal = value == null ? null : org.pms.silverocean.service.payment.money.MonetaryPolicy.amount(value);
        amount = amountDecimal == null ? null : amountDecimal.doubleValue();
    }

    @PostLoad
    private void readDecimalShadow() {
        if (amountDecimal != null) amount = amountDecimal.doubleValue();
    }

    @PrePersist @PreUpdate
    private void writeDecimalShadow() {
        amountDecimal = amount == null ? null : org.pms.silverocean.service.payment.money.MonetaryPolicy.amount(amount);
        if (currencyCode != null) currencyCode = org.pms.silverocean.service.payment.money.MonetaryPolicy.currency(currencyCode);
    }
}
