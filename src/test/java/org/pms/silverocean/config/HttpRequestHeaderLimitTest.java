package org.pms.silverocean.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.unit.DataSize;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HttpRequestHeaderLimitTest {

    private static final int FRONTEND_TOKEN_CHUNK_SIZE_BYTES = 3_500;
    private static final int FRONTEND_MAX_TOKEN_CHUNKS = 4;
    private static final int STANDARD_BROWSER_HEADER_BUDGET_BYTES = 8 * 1_024;

    @Test
    void applicationAllowsTheBoundedFrontendAccessTokenEnvelope() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        MutablePropertySources propertySources = new MutablePropertySources();
        sources.forEach(propertySources::addLast);
        ServerProperties properties = new Binder(ConfigurationPropertySources.from(propertySources))
                .bind("server", Bindable.of(ServerProperties.class))
                .orElseThrow(() -> new AssertionError("server configuration was not bound"));

        DataSize configuredLimit = properties.getMaxHttpRequestHeaderSize();
        long supportedRequestEnvelope =
                (long) FRONTEND_TOKEN_CHUNK_SIZE_BYTES * FRONTEND_MAX_TOKEN_CHUNKS
                        + STANDARD_BROWSER_HEADER_BUDGET_BYTES;

        assertThat(configuredLimit).isEqualTo(DataSize.ofKilobytes(32));
        assertThat(configuredLimit.toBytes()).isGreaterThan(supportedRequestEnvelope);
    }
}
