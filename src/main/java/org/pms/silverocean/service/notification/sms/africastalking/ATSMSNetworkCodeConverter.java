package org.pms.silverocean.service.notification.sms.africastalking;

import org.pms.silverocean.service.notification.sms.africastalking.wrappers.ATSMSNetworkCode;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class ATSMSNetworkCodeConverter implements Converter<String, ATSMSNetworkCode> {
    @Override
    public ATSMSNetworkCode convert(String value) {
        return Arrays.stream(ATSMSNetworkCode.values())
                .filter(cfg -> String.valueOf(cfg.getCode()).equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid network code: " + value));
    }
}
