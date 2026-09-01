package org.pms.silverocean.service.wealth.market;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;

public interface MarketQuoteProvider {
    record Quote(String currency, BigDecimal price, BigDecimal previousClose,
                 BigDecimal change, BigDecimal changePercent, String provider,
                 String freshness, ZonedDateTime asOf) {}
    Optional<Quote> quote(String exchange, String symbol, String currency);
    boolean available();
}
