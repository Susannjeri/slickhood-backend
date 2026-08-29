package org.pms.silverocean.service.notification.sms.africastalking;

public record CurrencyParseResult( String currency,
                                   double cost,
                                   String errorMessage,
                                   boolean isSuccess
) {
    // Factory method for a successful parse
    public static CurrencyParseResult success(String currency, double cost) {
        return new CurrencyParseResult(currency, cost, null, true);
    }

    // Factory method for a failed parse
    public static CurrencyParseResult failure(String message) {
        return new CurrencyParseResult(null, 0.0, message, false);
    }
}
