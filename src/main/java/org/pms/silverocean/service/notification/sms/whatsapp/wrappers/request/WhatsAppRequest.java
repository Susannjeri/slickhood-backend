package org.pms.silverocean.service.notification.sms.whatsapp.wrappers.request;

public record WhatsAppRequest(String messaging_product,
                              String to,
                              String type,
                              Template template) {
}
