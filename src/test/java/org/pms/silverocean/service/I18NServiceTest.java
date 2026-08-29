package org.pms.silverocean.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.pms.silverocean.common.ResponseCode;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class I18NServiceTest {

    private final I18NService i18NService = createService();

    private static I18NService createService() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        return new I18NService(source);
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void testGetLocalizedMessage_defaultLocale() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        String message = i18NService.getLocalizedMessage("response.code.LOGIN_SUCCESS");
        assertThat(message).isEqualTo("Login Success.");
    }

    @Test
    void testGetLocalizedMessage_withFrenchLocale() {
        LocaleContextHolder.setLocale(Locale.FRENCH);
        String message = i18NService.getLocalizedMessage("response.code.LOGIN_SUCCESS");
        assertThat(message).isEqualTo("Bienvenue Connexion."); // adjust to your messages_fr.properties
    }

    @Test
    void testGetLocalizedMessage_withDefaultMessageFallback() {
        LocaleContextHolder.setLocale(Locale.GERMAN);
        String message = i18NService.getLocalizedMessage("non.existing.key", "Fallback text");
        assertThat(message).isEqualTo("Fallback text");
    }

    @Test
    void testGetLocalizedMessage_usingResponseCode() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        String message = i18NService.getLocalizedMessage(ResponseCode.LOGIN_SUCCESS);
        assertThat(message).isEqualTo("Login Success.");
    }

    @Test
    void testGetSystemLocalizedMessage_alwaysEnglish() {
        LocaleContextHolder.setLocale(Locale.FRENCH);
        String message = i18NService.getSystemLocalizedMessage(ResponseCode.LOGIN_SUCCESS);
        assertThat(message).isEqualTo("Login Success."); // always English regardless of locale
    }
}
