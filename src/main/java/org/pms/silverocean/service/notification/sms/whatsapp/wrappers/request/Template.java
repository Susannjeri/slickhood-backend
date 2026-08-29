package org.pms.silverocean.service.notification.sms.whatsapp.wrappers.request;

import java.util.List;

public record Template(String name,
                       Language language,
                       List<Component> components) {
}
