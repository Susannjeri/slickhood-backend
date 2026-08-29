package org.pms.silverocean.service.payment.platforms.flutterwave.wrappers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL) @JsonIgnoreProperties(ignoreUnknown = true)
public record FWCard(@JsonProperty("first_6digits") String first6Digits,
                     @JsonProperty("last_4digits") String last4Digits,
                     String issuer,
                     String country,
                     String type,
                     String expiry) {

    public @NotNull String toString() {
        return "{" +
                "\"first6Digits\":\"" + first6Digits + "\"," +
                "\"last4Digits\":\"" + last4Digits + "\"," +
                "\"issuer\":\"" + issuer + "\"," +
                "\"country\":\"" + country + "\"," +
                "\"type\":\"" + type + "\"," +
                "\"expiry\":\"hidden\"" +
                "}";
    }
}
