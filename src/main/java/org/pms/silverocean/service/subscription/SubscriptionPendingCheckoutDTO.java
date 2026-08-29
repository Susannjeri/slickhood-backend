package org.pms.silverocean.service.subscription;

/**
 * Paid plan checkout: use {@code invoiceRef} with {@code GET /payment/init}.
 */
public record SubscriptionPendingCheckoutDTO(
        String invoiceRef,
        String currency,
        double amount,
        String planCode,
        String role
) {
}
