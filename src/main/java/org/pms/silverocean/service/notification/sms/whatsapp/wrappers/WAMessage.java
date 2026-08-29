package org.pms.silverocean.service.notification.sms.whatsapp.wrappers;

import com.fasterxml.jackson.annotation.JsonProperty;

//[{"id":"wamid.HBgMMjU0NzE1NzAyODg3FQIAERgSNDAwREExMDg0REMwNkFEQkU1AA==","message_status":"accepted"}]
public record WAMessage(String id, @JsonProperty("message_status") String messageStatus) {
}
