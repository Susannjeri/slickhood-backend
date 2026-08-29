package org.pms.silverocean.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.textract.TextractClient;

@Configuration
public class KycOcrConfig {
    @Bean
    @ConditionalOnProperty(name = "kyc.ocr.provider", havingValue = "aws-textract")
    TextractClient textractClient(@Value("${kyc.ocr.aws.region:af-south-1}") String region) {
        return TextractClient.builder().region(Region.of(region)).build();
    }
}
