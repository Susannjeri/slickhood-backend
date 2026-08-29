package org.pms.silverocean.service.notification.sms.whatsapp.wrappers;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

//{"messaging_product":"whatsapp","contacts":[{"input":"254715702887","wa_id":"254715702887"}],"messages":[{"id":"wamid.HBgMMjU0NzE1NzAyODg3FQIAERgSNDAwREExMDg0REMwNkFEQkU1AA==","message_status":"accepted"}]}
public record WhatsAppResponse(@JsonProperty("messaging_product") String messagingProduct, List<WAContact> contacts, List<WAMessage> messages) {
}
