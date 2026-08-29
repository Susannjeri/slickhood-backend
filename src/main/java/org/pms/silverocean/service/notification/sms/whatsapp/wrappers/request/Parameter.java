package org.pms.silverocean.service.notification.sms.whatsapp.wrappers.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Parameter(String type,
                        @JsonProperty("parameter_name") String parameterName,
                        String text) {
}
