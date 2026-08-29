package org.pms.silverocean.service.notification.sms.whatsapp.wrappers.callback;

import java.util.List;

public record WAEntry(String id,
                      List<WAChange> changes) {
}
