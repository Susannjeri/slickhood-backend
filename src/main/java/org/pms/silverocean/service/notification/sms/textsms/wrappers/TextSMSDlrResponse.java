package org.pms.silverocean.service.notification.sms.textsms.wrappers;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TextSMSDlrResponse(@JsonProperty("response-code")
                                 int responseCode,

                                 @JsonProperty("message-id")
                                 String messageId,

                                 @JsonProperty("response-description")
                                 String responseDescription,

                                 @JsonProperty("delivery-status")
                                 int deliveryStatus,

                                 @JsonProperty("delivery-description")
                                 String deliveryDescription,

                                 @JsonProperty("delivery-tat")
                                 String deliveryTat,

                                 @JsonProperty("delivery-networkid")
                                 int deliveryNetworkId,

                                 @JsonProperty("delivery-time")
                                 String deliveryTime) {
}
