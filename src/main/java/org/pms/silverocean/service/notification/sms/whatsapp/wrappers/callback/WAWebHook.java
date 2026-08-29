package org.pms.silverocean.service.notification.sms.whatsapp.wrappers.callback;

import java.util.List;

public record WAWebHook(String object,
                        List<WAEntry> entry) {
}
