package org.pms.silverocean.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3Config {
    @Value("${garage.s3.access.key:}")
    private String garageAccessKey;
    @Value("${garage.s3.secret.key:}")
    private String garageSecretKey;

    @Value("${garage.s3.url:}")
    private String s3GarageUrl;

    @Value("${garage.presigner.url:}")
    private String presignerGarageUrl;

    @Value("${garage.s3.region:ap-south-1}")
    private String region;

    @Value("${garage.s3.path-style:false}")
    private boolean pathStyle;

    @Value("${garage.s3.require-https:false}")
    private boolean requireHttps;

    @Bean
    public S3Client s3Client() {
        validateEndpoint(s3GarageUrl);
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .forcePathStyle(pathStyle)
                .serviceConfiguration(S3Configuration.builder()
                        .chunkedEncodingEnabled(false)
                        .build());
        if (!s3GarageUrl.isBlank()) {
            builder.endpointOverride(URI.create(s3GarageUrl));
        }
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        validateEndpoint(presignerGarageUrl);
        var builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyle)
                        .build());
        if (!presignerGarageUrl.isBlank()) {
            builder.endpointOverride(URI.create(presignerGarageUrl));
        }
        return builder.build();
    }

    AwsCredentialsProvider credentialsProvider() {
        boolean hasAccessKey = garageAccessKey != null && !garageAccessKey.isBlank();
        boolean hasSecretKey = garageSecretKey != null && !garageSecretKey.isBlank();
        if (hasAccessKey != hasSecretKey) {
            throw new IllegalStateException("Both S3 access and secret keys must be configured together");
        }
        if (hasAccessKey) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(garageAccessKey, garageSecretKey));
        }
        return DefaultCredentialsProvider.create();
    }

    void validateEndpoint(String endpoint) {
        if (requireHttps && endpoint != null && !endpoint.isBlank()
                && !endpoint.toLowerCase(java.util.Locale.ROOT).startsWith("https://")) {
            throw new IllegalStateException("Production S3 endpoint overrides must use HTTPS");
        }
    }
}
