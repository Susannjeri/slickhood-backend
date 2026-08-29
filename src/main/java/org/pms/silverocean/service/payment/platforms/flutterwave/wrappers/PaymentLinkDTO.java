package org.pms.silverocean.service.payment.platforms.flutterwave.wrappers;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.pms.silverocean.database.pms.entities.PMSInvoice;

public record PaymentLinkDTO(
        @JsonProperty("tx_ref")
        String invoiceRef,
        String amount,
        String currency,
        @JsonProperty("redirect_url")
        String redirectUrl,
        FWCustomer customer,
        FWCustomizations customizations,
        FWConfigurations configurations
) {

    public PaymentLinkDTO(long paymentId, PMSInvoice pmsInvoice, String redirectUrl, FWCustomer customer, int sessionDuration, int maxRetries, FWCustomizations customizations) {
        this(String.valueOf(paymentId), String.valueOf(pmsInvoice.getAmount()), pmsInvoice.getCurrency(), redirectUrl, customer,
                customizations, new FWConfigurations((int) Math.ceil(sessionDuration / 60.0), maxRetries));
    }

    @Override
    public String toString() {
        return "{\"tx_ref\": \"" + invoiceRef + "\", " +
                "\"amount\": \"" + amount + "\", " +
                "\"currency\": \"" + currency + "\", " +
                "\"redirectUrl\": \"" + redirectUrl + "\", " +
                "\"customer\": " + customer.toString() + ", " +
                "\"customizations\": {\"title\":\"" + customizations.title() + "\",  \"description\":\"" + customizations.description() + "\",  \"logo\":\"" + customizations.logo() + "\"}," +
                "\"configurations\": {\"sessionDuration\":\"" + configurations.sessionDuration() + "\",  \"maxRetries\":\"" + configurations.maxRetries() + "\"}" +
                "}";
    }
}
