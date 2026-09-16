package org.pms.silverocean.service.payment.currency;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class CurrencyPreferenceModels {
    private CurrencyPreferenceModels() {}

    public record CurrencyOption(String code, String name, String symbol, int fractionDigits) {}
    public record View(String defaultCurrency, List<String> enabledCurrencies,
                       List<CurrencyOption> availableCurrencies, long version) {}
    public record Update(@NotBlank @Size(min = 3, max = 3) String defaultCurrency,
                         @NotNull @Size(min = 1, max = 25) List<@NotBlank String> enabledCurrencies,
                         long version) {}
}
