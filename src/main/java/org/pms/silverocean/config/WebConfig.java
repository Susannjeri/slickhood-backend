package org.pms.silverocean.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.util.Locale;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final SubscriptionEntitlementInterceptor subscriptionEntitlementInterceptor;

    public WebConfig(SubscriptionEntitlementInterceptor subscriptionEntitlementInterceptor) {
        this.subscriptionEntitlementInterceptor = subscriptionEntitlementInterceptor;
    }
    @Bean
    public LocaleResolver localeResolver() {
        SessionLocaleResolver resolver = new SessionLocaleResolver();
        resolver.setDefaultLocale(Locale.ENGLISH);
        return resolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang"); // e.g. ?lang=fr
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
        registry.addInterceptor(subscriptionEntitlementInterceptor)
                .addPathPatterns(
                        "/wealth/**", "/smart-gate/**", "/soko/**", "/sp/**", "/affiliate/**",
                        "/property/**", "/lease/**", "/estate/**", "/sales/**",
                        "/community-funds/**", "/maintenance/**", "/visitor/**",
                        "/invoice/**", "/reports/**");
    }
}
