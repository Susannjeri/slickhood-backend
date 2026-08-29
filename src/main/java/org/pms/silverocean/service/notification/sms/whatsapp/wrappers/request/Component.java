package org.pms.silverocean.service.notification.sms.whatsapp.wrappers.request;

import java.util.List;

public record Component(String type,
                        List<Parameter> parameters) {
}
