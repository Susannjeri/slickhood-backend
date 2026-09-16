package org.pms.silverocean.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.payment.currency.CurrencyPreferenceModels;
import org.pms.silverocean.service.payment.currency.CurrencyPreferenceService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrencyPreferenceControllerSecurityTest {
    @Configuration @EnableMethodSecurity static class SecurityConfig {
        @Bean CurrencyPreferenceService service() { return mock(CurrencyPreferenceService.class); }
        @Bean I18NService i18n() { return mock(I18NService.class); }
        @Bean CurrencyPreferenceController controller(CurrencyPreferenceService service, I18NService i18n) {
            return new CurrencyPreferenceController(service, i18n);
        }
    }

    private AnnotationConfigApplicationContext context;
    @BeforeEach void open() { context = new AnnotationConfigApplicationContext(SecurityConfig.class); }
    @AfterEach void close() { SecurityContextHolder.clearContext(); context.close(); }

    @Test void anonymousUsersCannotReadCurrencyPreferences() {
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> context.getBean(CurrencyPreferenceController.class).current());
        verifyNoInteractions(context.getBean(CurrencyPreferenceService.class));
    }

    @Test void everyAuthenticatedProfileCanReadItsOwnNonCachedPreferences() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("fixture-user", "unused", List.of()));
        var view = new CurrencyPreferenceModels.View("KES", List.of("KES"), List.of(), 0);
        when(context.getBean(CurrencyPreferenceService.class).current()).thenReturn(view);

        var response = context.getBean(CurrencyPreferenceController.class).current();

        assertEquals(200, response.getStatusCode().value());
        assertEquals("no-store", response.getHeaders().getCacheControl());
    }
}
