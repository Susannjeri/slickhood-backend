package org.pms.silverocean.service.notification.sms.textsms.wrappers;

import java.util.Set;

public record TextSMSResponse(
        Set<SmsResponseItem> responses
) {}
