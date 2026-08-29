package org.pms.silverocean.service.notification.sms.whatsapp.wrappers.callback;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WAStatus(String id,
                       String status,
                       String timestamp,
                       @JsonProperty("recipient_id") String recipientId,
                       WAPricing pricing) {
}
