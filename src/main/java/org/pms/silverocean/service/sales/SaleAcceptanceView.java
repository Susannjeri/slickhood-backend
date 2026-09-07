package org.pms.silverocean.service.sales;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.pms.silverocean.database.pms.entities.SaleTransaction;

/** Buyer-facing acknowledgement; never serialize the internal sale entity to the buyer. */
public record SaleAcceptanceView(long id, SaleStatus status, BigDecimal offerAmount,
                                 String currency, LocalDateTime offerAcceptedAt) {
    public SaleAcceptanceView(SaleTransaction sale) {
        this(sale.getId(), sale.getStatus(), sale.getOfferAmount(), sale.getCurrency(), sale.getOfferAcceptedAt());
    }
}
