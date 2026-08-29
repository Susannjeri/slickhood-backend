package org.pms.silverocean.service.notification.sms.whatsapp.wrappers;

import lombok.Getter;

@Getter
public enum WhatsAppStatus {
    DELIVERED("delivered"), SENT("sent"), ACCEPTED("accepted");

    private final String name;

    WhatsAppStatus(String name) {
        this.name = name;
    }
}
