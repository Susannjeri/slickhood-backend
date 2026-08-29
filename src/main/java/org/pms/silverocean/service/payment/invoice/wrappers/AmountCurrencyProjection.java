package org.pms.silverocean.service.payment.invoice.wrappers;

import java.math.BigDecimal;

public interface AmountCurrencyProjection {
    BigDecimal getAmount();
    String getCurrency();
}
