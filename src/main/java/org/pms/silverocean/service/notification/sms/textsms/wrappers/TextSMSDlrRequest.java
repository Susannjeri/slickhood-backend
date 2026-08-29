package org.pms.silverocean.service.notification.sms.textsms.wrappers;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TextSMSDlrRequest(@JsonProperty("partnerID") String partnerId,
                                @JsonProperty("apikey") String apiKey,
                                @JsonProperty("messageID") String messageId,
                                int retryCount
                                ) {
}
