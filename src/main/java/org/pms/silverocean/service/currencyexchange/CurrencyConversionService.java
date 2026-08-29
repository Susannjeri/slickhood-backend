package org.pms.silverocean.service.currencyexchange;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@Slf4j
public class CurrencyConversionService {

    private final ExchangeRateProvider exchangeRateProvider;

    public CurrencyConversionService(ExchangeRateProvider exchangeRateProvider) {
        this.exchangeRateProvider = exchangeRateProvider;
    }

    /**
     * Converts an amount from one currency to another using USD as the base pivot.
     * Formula: (Amount / SourceRate) * TargetRate
     */
    public BigDecimal convert(BigDecimal amount, String sourceCurrency, String targetCurrency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        String source = sourceCurrency.toUpperCase();
        String target = targetCurrency.toUpperCase();

        // 1. Short-circuit if currencies are the same
        if (source.equals(target)) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }

        // 2. Fetch rates relative to USD
        BigDecimal sourceRate = exchangeRateProvider.getCurrencyRate(source);
        BigDecimal targetRate = exchangeRateProvider.getCurrencyRate(target);

        // 3. Validate rates exist
        if (sourceRate == null || targetRate == null) {
            log.error("Conversion failed: Missing rates for {} or {}", source, target);
            throw new RuntimeException("Unsupported currency: " + (sourceRate == null ? source : target));
        }

        // 4. Perform the conversion
        // We divide first to get the value in USD, then multiply by target rate
        // We use a high scale (6) during intermediate steps to maintain precision
        BigDecimal amountInUsd = amount.divide(sourceRate, 6, RoundingMode.HALF_UP);
        BigDecimal convertedAmount = amountInUsd.multiply(targetRate);

        // 5. Final rounding for financial display (2 decimal places)
        return convertedAmount.setScale(2, RoundingMode.HALF_UP);
    }
}
