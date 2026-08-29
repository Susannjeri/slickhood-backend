package org.pms.silverocean.service.currencyexchange;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Map;

public record ExchangeRateResponse(String result,
                                   @JsonProperty("time_last_update_utc") String date,
                                   @JsonProperty("base_code") String base,
                                   @JsonProperty("conversion_rates") Map<String, BigDecimal> rates) {
}
