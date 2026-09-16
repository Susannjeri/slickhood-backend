package org.pms.silverocean.service.payment.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;

/** One monetary boundary for all SlickHood transactional values. */
public final class MonetaryPolicy {
    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private MonetaryPolicy() {}

    public static String currency(String value) {
        if (value == null) throw new IllegalArgumentException("Currency is required");
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            Currency iso = Currency.getInstance(normalized);
            if (iso.getDefaultFractionDigits() != SCALE)
                throw new IllegalArgumentException("Currency is not compatible with the current two-decimal ledger");
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("A supported ISO 4217 currency code is required", invalid);
        }
        return normalized;
    }

    public static BigDecimal amount(BigDecimal value) {
        if (value == null) throw new IllegalArgumentException("Amount is required");
        return value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal amount(Number value) {
        if (value == null) throw new IllegalArgumentException("Amount is required");
        if (value instanceof BigDecimal decimal) return amount(decimal);
        return amount(new BigDecimal(value.toString()));
    }

    public static BigDecimal positive(BigDecimal value) {
        BigDecimal normalized = amount(value);
        if (normalized.signum() <= 0) throw new IllegalArgumentException("Amount must be greater than zero");
        return normalized;
    }

    public static long toMinorUnits(BigDecimal value, String currency) {
        currency(currency);
        return amount(value).movePointRight(SCALE).longValueExact();
    }

    public static BigDecimal fromMinorUnits(long value, String currency) {
        currency(currency);
        return BigDecimal.valueOf(value, SCALE).setScale(SCALE, ROUNDING);
    }
}
