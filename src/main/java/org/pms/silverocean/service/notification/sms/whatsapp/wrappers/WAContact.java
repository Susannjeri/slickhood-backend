package org.pms.silverocean.service.notification.sms.whatsapp.wrappers;

import com.fasterxml.jackson.annotation.JsonProperty;
//[{"input":"254715702887","wa_id":"254715702887"}]
public record WAContact(String input, @JsonProperty("wa_id") String waId) {
}
