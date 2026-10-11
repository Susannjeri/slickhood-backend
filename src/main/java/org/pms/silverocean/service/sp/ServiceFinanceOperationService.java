package org.pms.silverocean.service.sp;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.ServiceFinanceOperationRepo;
import org.pms.silverocean.database.pms.entities.ServiceBooking;
import org.pms.silverocean.database.pms.entities.ServiceFinanceOperation;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.payment.money.MonetaryPolicy;
import org.pms.silverocean.service.sp.wrappers.MarketplaceFinanceRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ServiceFinanceOperationService {
    private final ServiceFinanceOperationRepo operations;

    /**
     * Appends a confirmed operation, or returns {@code true} for an exact retry.
     * A reused provider reference with any different immutable field is rejected.
     */
    public boolean recordConfirmed(ServiceBooking booking, long providerUserId,
                                   MarketplaceFinanceRequest.FinanceType type,
                                   BigDecimal amount, String providerReference, long initiatedBy) {
        if (booking == null || booking.getId() == null || providerUserId <= 0 || type == null) {
            throw invalid("The Services finance operation is incomplete.");
        }
        String channel = normalizedRequired(booking.getPaymentChannel(), 30,
                "The original payment channel is missing.").toUpperCase(Locale.ROOT);
        String reference = normalizedRequired(providerReference, 120,
                "Enter the external provider reference before confirming.");
        String currency;
        BigDecimal normalizedAmount;
        try {
            currency = MonetaryPolicy.currency(booking.getCurrency());
            normalizedAmount = MonetaryPolicy.positive(amount);
        } catch (IllegalArgumentException invalidMoney) {
            throw invalid("The Services finance amount or currency is invalid.");
        }

        var existing = operations
                .findByProviderUserIdAndPaymentChannelAndOperationTypeAndProviderReference(
                        providerUserId, channel, type.name(), reference);
        if (existing.isPresent()) {
            if (sameOperation(existing.get(), booking.getId(), providerUserId, channel,
                    type.name(), reference, normalizedAmount, currency)) {
                return true;
            }
            throw invalid("This external provider reference is already linked to another Services finance operation.");
        }

        operations.saveAndFlush(new ServiceFinanceOperation(booking.getId(), providerUserId, channel,
                type.name(), reference, normalizedAmount, currency, initiatedBy));
        return false;
    }

    private boolean sameOperation(ServiceFinanceOperation existing, long bookingId, long providerUserId,
                                  String channel, String type, String reference,
                                  BigDecimal amount, String currency) {
        return existing.getBookingId() == bookingId
                && existing.getProviderUserId() == providerUserId
                && channel.equals(existing.getPaymentChannel())
                && type.equals(existing.getOperationType())
                && "CONFIRMED".equals(existing.getOperationStatus())
                && reference.equals(existing.getProviderReference())
                && amount.compareTo(existing.getAmount()) == 0
                && currency.equals(existing.getCurrency());
    }

    private String normalizedRequired(String value, int maximumLength, String message) {
        String normalized = StringUtils.trimToNull(value);
        if (normalized == null || normalized.length() > maximumLength) {
            throw invalid(message);
        }
        return normalized;
    }

    private PMSCustomException invalid(String message) {
        return new PMSCustomException(ResponseCode.INVALID_FIELD_DATA, message);
    }
}
