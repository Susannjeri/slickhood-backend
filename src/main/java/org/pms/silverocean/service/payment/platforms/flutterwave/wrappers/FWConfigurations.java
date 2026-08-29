package org.pms.silverocean.service.payment.platforms.flutterwave.wrappers;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FWConfigurations(
        @JsonProperty("session_duration")
        int sessionDuration,
        @JsonProperty("max_retry_attempt")
        int maxRetries) {
}
