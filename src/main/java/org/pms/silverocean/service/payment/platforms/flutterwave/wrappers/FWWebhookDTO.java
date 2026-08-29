package org.pms.silverocean.service.payment.platforms.flutterwave.wrappers;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;


public record FWWebhookDTO<T>(
        String event,
        @JsonProperty("event.type")
        String eventType,
        T data
) {
    @Override
    public @NotNull String toString() {
        return "FWWebhookDTO [event=" + event + ", eventType=" + eventType +"]";
    }
}
