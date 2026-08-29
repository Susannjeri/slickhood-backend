package org.pms.silverocean.service.notification.sms.textsms.wrappers;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SmsResponseItem(
        @JsonProperty("response-code") int responseCode,
        @JsonProperty("response-description") String responseDescription,
        long mobile,
        @JsonProperty("messageid")  String thirdPartyMessageId,
        @JsonProperty("networkid")  long networkId
) {}