package org.pms.silverocean.service.payment.money;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) implements Comparable<Money> {
    public Money {
        amount = MonetaryPolicy.amount(amount);
        currency = MonetaryPolicy.currency(currency);
    }

    public static Money of(Number amount, String currency) {
        return new Money(MonetaryPolicy.amount(amount), currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public long minorUnits() { return MonetaryPolicy.toMinorUnits(amount, currency); }

    @Override public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        if (other == null || !currency.equals(other.currency))
            throw new IllegalArgumentException("Money currencies do not match");
    }
}
