package org.pms.silverocean.service.payment.platforms.mpesa.wrappers;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CallbackItem(@JsonProperty("Name") String name,
                           @JsonProperty("Value") Object value) {
}
