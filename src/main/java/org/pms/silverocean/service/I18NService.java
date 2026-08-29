package org.pms.silverocean.service;

import org.pms.silverocean.common.ResponseCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class I18NService {
    private final MessageSource messageSource;


    @Autowired
    public I18NService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }


    public String getLocalizedMessage(String key) {
        return messageSource.getMessage(key, new Object[0], key, LocaleContextHolder.getLocale());
    }

    public String getLocalizedMessage(String key, Locale locale) {
        return messageSource.getMessage(key, new Object[0], getLocalizedMessage(key), locale);
    }

    public String getLocalizedMessage(String key, String defaultMessage) {
        return messageSource.getMessage(key, new Object[0], defaultMessage, LocaleContextHolder.getLocale());
    }

    public String getLocalizedMessage(ResponseCode responseCode) {
        return getLocalizedMessage(responseCode.getDescription());
    }

    public String getLocalizedMessage(ResponseCode responseCode, String defaultMessage) {
        return getLocalizedMessage(responseCode.getDescription(), defaultMessage);
    }

    public String getSystemLocalizedMessage(ResponseCode responseCode) {
        return messageSource.getMessage(responseCode.getDescription(), new Object[0], responseCode.getDescription(), Locale.ENGLISH);
    }
}
