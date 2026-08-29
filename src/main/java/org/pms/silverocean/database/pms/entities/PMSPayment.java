package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
    private String customerAccountNumber;
    private String receivingAccountNumber;
    private String billReference;
    private String channel;
    private String category;
    private String customerName;

    private String thirdPartyTransId;
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
        this.amount = StringUtils.isNotBlank(mpesaPaymentDTO.transAmount()) ? Double.parseDouble(mpesaPaymentDTO.transAmount()) : null;
        this.billReference = mpesaPaymentDTO.billRefNumber();
        this.customerAccountNumber = mpesaPaymentDTO.msisdn();
        this.customerName = String.format("%s %s %s", mpesaPaymentDTO.firstName(), mpesaPaymentDTO.middleName(), mpesaPaymentDTO.lastName());
        this.channel = PaymentChannel.MPESA.getName();
        this.category = transactionCategory.name();
        this.receivingAccountNumber = mpesaPaymentDTO.businessShortCode();
    }

    public PMSPayment(MPesaSTKPushRequest mpesaSTKPushRequest, long accountId) {
        this.amount = Double.parseDouble(mpesaSTKPushRequest.amount());
        this.customerAccountNumber = mpesaSTKPushRequest.phoneNumber();
        this.billReference = mpesaSTKPushRequest.accountReference();
        this.channel = PaymentChannel.MPESA.getName();
        this.category = TransactionCategory.STK.name();
        this.receivingAccountNumber = mpesaSTKPushRequest.businessShortCode();
        this.accountId = accountId;
    }

    public PMSPayment(PMSInvoice pmsInvoice, String customerName, long accountId) {
        this.amount = pmsInvoice.getPendingAmount();
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
        this.amount = manualPaymentDTO.amount();
        this.category = TransactionCategory.MANUAL_RECORD.name();
        this.channel = manualPaymentDTO.channel();
        this.thirdPartyTransId = manualPaymentDTO.transId();
        this.status = "success";
    }

    public PMSPayment(PesalinkValidatePaymentRequestDTO pesalinkValidatePaymentRequestDTO, TransactionCategory transactionCategory) {
        this.thirdPartyTransId = pesalinkValidatePaymentRequestDTO.requestId();
        this.amount = pesalinkValidatePaymentRequestDTO.amount().doubleValue();
        this.billReference = pesalinkValidatePaymentRequestDTO.billRef();
        this.channel = PaymentChannel.PESA_LINK.getName();
        this.category = transactionCategory.name();
    }

    public PMSPayment(IPNCallbackDTO ipnCallbackDTO, TransactionCategory transactionCategory) {
        this.thirdPartyTransId = ipnCallbackDTO.rrn();
        this.amount = ipnCallbackDTO.amount().doubleValue();
        this.billReference = ipnCallbackDTO.billReference();
        this.channel = PaymentChannel.PESA_LINK.getName();
        this.category = transactionCategory.name();
    }

    public boolean isCompletedSuccessfully() {
        return inProgress ? false : TransactionCategory.valueOf(category).getSuccessString().equals(status);
    }
}
