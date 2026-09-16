package org.pms.silverocean.service.payment;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PMSPayment;
import org.pms.silverocean.service.payment.money.Money;
import org.pms.silverocean.service.payment.money.MonetaryPolicy;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MonetaryPolicyTest {
    @Test void supportsTwoDecimalIsoTransactionalCurrencies() {
        assertEquals("KES", MonetaryPolicy.currency(" kes "));
        assertEquals("USD", MonetaryPolicy.currency("usd"));
        assertEquals("EUR", MonetaryPolicy.currency("eur"));
        assertEquals("GBP", MonetaryPolicy.currency("gbp"));
        assertEquals("ZAR", MonetaryPolicy.currency("zar"));
        assertThrows(IllegalArgumentException.class, () -> MonetaryPolicy.currency("ZZZ"));
        assertThrows(IllegalArgumentException.class, () -> MonetaryPolicy.currency("JPY"));
    }

    @Test void appliesOneDocumentedRoundingRule() {
        assertEquals(new BigDecimal("10.01"), MonetaryPolicy.amount(new BigDecimal("10.005")));
        assertEquals(new BigDecimal("10.00"), MonetaryPolicy.amount(new BigDecimal("10.004")));
    }

    @Test void convertsProviderMinorUnitsWithoutFloatingPointArithmetic() {
        Money value = new Money(new BigDecimal("1234.56"), "KES");
        assertEquals(123456L, value.minorUnits());
        assertEquals(value.amount(), MonetaryPolicy.fromMinorUnits(123456L, "KES"));
    }

    @Test void rejectsCrossCurrencyArithmetic() {
        Money kes = new Money(new BigDecimal("100.00"), "KES");
        Money usd = new Money(new BigDecimal("100.00"), "USD");
        assertThrows(IllegalArgumentException.class, () -> kes.add(usd));
    }

    @Test void coreEntitiesDualWriteExactShadowValues() {
        PMSInvoice invoice = new PMSInvoice();
        invoice.setMoneyAmount(new BigDecimal("1000.10"));
        invoice.setMoneyPendingAmount(new BigDecimal("700.05"));
        assertEquals(new BigDecimal("1000.10"), invoice.getAmountDecimal());
        assertEquals(new BigDecimal("700.05"), invoice.moneyPendingAmount());

        PMSPayment payment = new PMSPayment();
        payment.setMoneyAmount(new BigDecimal("300.05"));
        assertEquals(new BigDecimal("300.05"), payment.getAmountDecimal());
        assertEquals(new BigDecimal("300.05"), payment.moneyAmount());
    }
}
