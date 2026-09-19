package org.pms.silverocean.service.payment.wrappers;

import org.pms.silverocean.database.pms.entities.PMSPayment;
import org.pms.silverocean.service.payment.platforms.mpesa.TransactionCategory;

import java.time.ZonedDateTime;
import java.math.BigDecimal;

public record PaymentDTO(long id, BigDecimal amount, String customerName, String customerAccount, String transId,
                         String channel,
                         String category, ZonedDateTime createdOn, String status,
                         String description, boolean inProgress, boolean success) {
    public PaymentDTO(PMSPayment pmsPayment, String statusDesc) {
        this(pmsPayment.getId(), pmsPayment.moneyAmount(), pmsPayment.getCustomerName(), pmsPayment.getCustomerAccountNumber(), pmsPayment.getThirdPartyTransId(),
                channel(pmsPayment.getChannel()), category(pmsPayment.getCategory()),
                pmsPayment.getCreatedOn(), pmsPayment.getStatus(),
                statusDesc, pmsPayment.isInProgress(), pmsPayment.isCompletedSuccessfully());
    }

    private static String channel(String value) {
        try { return PaymentChannel.fromName(value).name(); }
        catch (IllegalArgumentException ignored) { return value == null || value.isBlank() ? "UNKNOWN" : value; }
    }

    private static String category(String value) {
        try { return TransactionCategory.valueOf(value).name(); }
        catch (IllegalArgumentException | NullPointerException ignored) { return value == null || value.isBlank() ? "UNKNOWN" : value; }
    }
}
