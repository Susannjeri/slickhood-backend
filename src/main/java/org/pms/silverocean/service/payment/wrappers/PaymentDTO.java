package org.pms.silverocean.service.payment.wrappers;

import org.pms.silverocean.database.pms.entities.PMSPayment;
import org.pms.silverocean.service.payment.platforms.mpesa.TransactionCategory;

import java.time.ZonedDateTime;

public record PaymentDTO(long id, Double amount, String customerName, String customerAccount, String transId,
                         PaymentChannel channel,
                         TransactionCategory category, ZonedDateTime createdOn, String status,
                         String description, boolean inProgress, boolean success) {
    public PaymentDTO(PMSPayment pmsPayment, String statusDesc) {
        this(pmsPayment.getId(), pmsPayment.getAmount(), pmsPayment.getCustomerName(), pmsPayment.getCustomerAccountNumber(), pmsPayment.getThirdPartyTransId(),
                PaymentChannel.fromName(pmsPayment.getChannel()), TransactionCategory.valueOf(pmsPayment.getCategory()),
                pmsPayment.getCreatedOn(), pmsPayment.getStatus(),
                statusDesc, pmsPayment.isInProgress(), pmsPayment.isCompletedSuccessfully());
    }
}
