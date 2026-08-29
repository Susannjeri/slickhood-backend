package org.pms.silverocean.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class CustomBeans {
    @Value("${http.client.connection.timeout:60}")
    private int clientConnectionTimeout;
    @Value("${http.client.read.timeout:60}")
    private int clientReadTimeout;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(clientConnectionTimeout))
                .readTimeout(Duration.ofSeconds(clientReadTimeout))
                .build();
    }
}
