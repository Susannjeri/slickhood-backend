package org.pms.silverocean.service.notification.sms.whatsapp.wrappers.callback;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record WAValue(@JsonProperty("messaging_product") String messagingProduct,
                      WAMetadata metadata,
                      List<WAStatus> statuses) {
}
