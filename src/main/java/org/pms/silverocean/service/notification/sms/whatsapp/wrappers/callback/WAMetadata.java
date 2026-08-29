package org.pms.silverocean.service.notification.sms.whatsapp.wrappers.callback;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WAMetadata(@JsonProperty("display_phone_number") String displayPhoneNumber,
                         @JsonProperty("phone_number_id") String phoneNumberId) {
}
