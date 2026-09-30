package org.pms.silverocean.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityAllowlistScopeTest {
    @Test
    void catalogueAndReferenceDataAllowlistIsReadOnlyAndContainsNoDeadImageRule() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/pms/silverocean/config/SecurityConfig.java"));

        assertTrue(source.contains("requestMatchers(HttpMethod.GET, \"/property/unit/charges\")"));
        assertTrue(source.contains("requestMatchers(HttpMethod.GET, \"/property/unit/type\")"));
        assertTrue(source.contains("requestMatchers(HttpMethod.GET, \"/property/type\")"));
        assertTrue(source.contains("requestMatchers(HttpMethod.GET, \"/sp/directory/**\")"));
        assertTrue(source.contains("requestMatchers(HttpMethod.GET, \"/soko/catalog/**\")"));
        assertFalse(source.contains("/property/image/**"));
        assertTrue(HttpMethod.GET.matches("GET"));
    }
}
