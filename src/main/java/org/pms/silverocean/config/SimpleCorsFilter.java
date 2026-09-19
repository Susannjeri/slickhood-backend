package org.pms.silverocean.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Class name: SimpleCorsFilter
 * Creater: wgicheru
 * Date:2/3/2020
 */
@Configuration
public class SimpleCorsFilter {
    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String[] allowedOrigins;

    @Value("${app.cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}")
    private String[] allowedMethods;

    @Value("${app.cors.allowed-headers:Authorization,Content-Type,X-Slickhood-Role,X-Correlation-Id}")
    private String[] allowedHeaders;

    @Value("${app.cors.exposed-headers:Content-Disposition,X-Report-Truncated,X-Report-Row-Limit}")
    private String[] exposedHeaders;

    @Value("${app.cors.require-https:false}")
    private boolean requireHttps;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = Arrays.stream(allowedOrigins)
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (origins.isEmpty()) {
            throw new IllegalStateException("At least one CORS origin must be configured");
        }
        if (requireHttps && origins.stream().anyMatch(origin -> !origin.startsWith("https://"))) {
            throw new IllegalStateException("Production CORS origins must use HTTPS");
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(Arrays.asList(allowedMethods));
        config.setAllowedHeaders(Arrays.asList(allowedHeaders));
        config.setExposedHeaders(Arrays.asList(exposedHeaders));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // The public insurance website is hosted separately from the application.
        // Keep its guest-only CORS policy separate from authenticated APIs.
        CorsConfiguration publicWebsite = new CorsConfiguration();
        var publicWebsiteOrigins = new java.util.LinkedHashSet<>(origins);
        publicWebsiteOrigins.add("https://slickhood.com");
        publicWebsiteOrigins.add("https://www.slickhood.com");
        publicWebsite.setAllowedOrigins(List.copyOf(publicWebsiteOrigins));
        publicWebsite.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        publicWebsite.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-Insurance-Access",
                "X-Slickhood-Role", "X-Slickhood-Workspace", "X-Correlation-Id"));
        publicWebsite.setAllowCredentials(false);
        source.registerCorsConfiguration("/public/insurance/**", publicWebsite);
        source.registerCorsConfiguration("/public/property-listings/**", publicWebsite);
        source.registerCorsConfiguration("/**", config);
        return source;
    }

}
