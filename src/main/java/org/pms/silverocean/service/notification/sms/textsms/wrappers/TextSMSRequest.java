package org.pms.silverocean.service.notification.sms.textsms.wrappers;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TextSMSRequest(
        @JsonProperty("partnerID") String partnerId,
        @JsonProperty("apikey") String apiKey,
        String mobile,
        String message,
        String shortcode
) {}
