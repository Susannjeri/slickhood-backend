package org.pms.silverocean.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3ConfigTest {
    @Test
    void productionRejectsHttpEndpointOverrides() {
        S3Config config = configured("", "", true);
        assertThatThrownBy(() -> config.validateEndpoint("http://127.0.0.1:3900"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void absentStaticKeysUseAwsDefaultCredentialChain() {
        S3Config config = configured("", "", true);
        assertThat(config.credentialsProvider()).isInstanceOf(DefaultCredentialsProvider.class);
    }

    @Test
    void pairedStaticKeysRemainAvailableForGarageRollback() {
        S3Config config = configured("access", "secret", false);
        assertThat(config.credentialsProvider()).isInstanceOf(StaticCredentialsProvider.class);
    }

    @Test
    void partialStaticCredentialsAreRejected() {
        S3Config config = configured("access", "", false);
        assertThatThrownBy(config::credentialsProvider).isInstanceOf(IllegalStateException.class);
    }

    private S3Config configured(String access, String secret, boolean requireHttps) {
        S3Config config = new S3Config();
        ReflectionTestUtils.setField(config, "garageAccessKey", access);
        ReflectionTestUtils.setField(config, "garageSecretKey", secret);
        ReflectionTestUtils.setField(config, "requireHttps", requireHttps);
        return config;
    }
}
