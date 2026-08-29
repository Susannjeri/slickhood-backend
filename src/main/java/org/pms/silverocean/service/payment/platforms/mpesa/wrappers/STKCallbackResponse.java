package org.pms.silverocean.service.payment.platforms.mpesa.wrappers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public record STKCallbackResponse(@JsonProperty("Body") Body body) {
    public String toString() {
        return "{ " +
                "\"Body\": {  " +
                "\"stkCallback\": {" +
                "\"MerchantRequestID\": \"" + body.stkCallback().merchantRequestID() + "\"," +
                "\"CheckoutRequestID\": \"" + body.stkCallback().checkoutRequestID() + "\"," +
                "\"ResultCode\": " + body.stkCallback().resultCode() + "," +
                "\"ResultDesc\": \"" + body.stkCallback().resultDesc() + "\"" +
                Optional.ofNullable(body().stkCallback().callbackMetadata())
                        .map(callbackMetadata -> {
                            Set<String> items = callbackMetadata.items()
                                    .stream().map(callbackItem -> "{\"Name\": \"" + callbackItem.name() + "\"," + "\"WAValue\": \"" + callbackItem.value() + "\"}")
                                    .collect(Collectors.toSet());
                            return ",\"CallbackMetadata\": { " +
                                    "\"Item\": [{" +
                                    String.join(",", items) +
                                    "}]" +
                                    "}";
                        }).orElse("") +
                "} " +
                "}" +
                "}";
    }
}
