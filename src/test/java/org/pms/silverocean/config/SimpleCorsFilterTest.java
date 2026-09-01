package org.pms.silverocean.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleCorsFilterTest {
    @Test
    void productionRejectsHttpOrigins() {
        SimpleCorsFilter filter = configured("http://app.slickhood.com", true);
        assertThatThrownBy(filter::corsConfigurationSource)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void productionAcceptsHttpsOrigins() {
        SimpleCorsFilter filter = configured("https://app.slickhood.com", true);
        assertThatCode(filter::corsConfigurationSource).doesNotThrowAnyException();
    }

    @Test
    void localDevelopmentCanExplicitlyAllowHttp() {
        SimpleCorsFilter filter = configured("http://localhost:3000", false);
        assertThatCode(filter::corsConfigurationSource).doesNotThrowAnyException();
    }

    private SimpleCorsFilter configured(String origin, boolean requireHttps) {
        SimpleCorsFilter filter = new SimpleCorsFilter();
        ReflectionTestUtils.setField(filter, "allowedOrigins", new String[]{origin});
        ReflectionTestUtils.setField(filter, "allowedMethods", new String[]{"GET", "OPTIONS"});
        ReflectionTestUtils.setField(filter, "allowedHeaders", new String[]{"Authorization", "Content-Type"});
        ReflectionTestUtils.setField(filter, "requireHttps", requireHttps);
        return filter;
    }
}
