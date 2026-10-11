package org.pms.silverocean.service.sp.wrappers;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * Finance-safe Services marketplace work item. Customer addresses, booking
 * notes and completion evidence are deliberately excluded from this view.
 */
public record MarketplaceFinanceQueueDTO(
        long bookingId,
        String invoiceRef,
        String serviceName,
        String providerName,
        Long providerUserId,
        String customerName,
        BigDecimal quotedAmount,
        String currency,
        String paymentStatus,
        Long paymentAccountId,
        String paymentChannel,
        String refundStatus,
        BigDecimal refundedAmount,
        String refundReference,
        String settlementStatus,
        BigDecimal settledAmount,
        String settlementReference,
        boolean settlementOnHold,
        ZonedDateTime disputeDeadlineAt,
        ZonedDateTime createdOn
) {}
