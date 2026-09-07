package org.pms.silverocean.service.payment;

import java.math.BigDecimal;
import java.util.Objects;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.service.PMSCustomException;

/** Guards recorded finance decisions; it does not send refunds or payouts. */
public final class MarketplaceFinanceGuard {
    private MarketplaceFinanceGuard() {}
    public record Entry(String status, BigDecimal amount, String reference) {}

    /** Returns true for an exact replay of a confirmed record. */
    public static boolean validate(BigDecimal total, boolean paid, boolean completed, boolean refund,
                                   Entry requested, Entry existing, Entry other) {
        if (!paid || total == null || requested.amount() == null || requested.amount().signum() <= 0
                || (!refund && !completed)) throw invalid();
        if (!refund && ("REQUESTED".equals(other.status()) || "PROCESSING".equals(other.status()))) throw invalid();
        if ("CONFIRMED".equals(existing.status())) {
            if ("CONFIRMED".equals(requested.status()) && existing.amount() != null
                    && existing.amount().compareTo(requested.amount()) == 0
                    && Objects.equals(existing.reference(), requested.reference())) return true;
            throw invalid();
        }
        if ("CONFIRMED".equals(requested.status())
                && (requested.reference() == null || requested.reference().isBlank())) throw invalid();
        BigDecimal committed = "CONFIRMED".equals(other.status()) && other.amount() != null
                ? other.amount() : BigDecimal.ZERO;
        if (requested.amount().add(committed).compareTo(total) > 0) throw invalid();
        return false;
    }
    private static PMSCustomException invalid() {
        return new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
    }
}
