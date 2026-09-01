package org.pms.silverocean.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionModuleGuardrailsTest {
    @Test
    void reportsMissingRuntimeCapabilitiesWithoutExposingValues() {
        var guardrails = new ProductionModuleGuardrails(new MockEnvironment());
        guardrails.validateSafeBounds();

        var assessment = guardrails.assess();

        assertThat(assessment.ready()).isFalse();
        assertThat(assessment.missingOrUnsafeConfiguration()).contains("wealth.market.enabled=true",
                "wealth.vault.antivirus.required=true", "app.insurance.imap.enabled=true",
                "affiliate.commission-rate (must be explicit)", "one verified payment callback secret");
    }

    @Test
    void acceptsCompleteProductionConfiguration() {
        var environment = completeEnvironment();
        var guardrails = new ProductionModuleGuardrails(environment);
        guardrails.validateSafeBounds();

        assertThat(guardrails.assess().ready()).isTrue();
    }

    @Test
    void rejectsUnsafeSchedulerAndBatchConfigurationAtStartup() {
        var environment = new MockEnvironment().withProperty("wealth.market.batch-size", "1000");

        assertThatThrownBy(() -> new ProductionModuleGuardrails(environment).validateSafeBounds())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wealth.market.batch-size");
    }

    private MockEnvironment completeEnvironment() {
        return new MockEnvironment()
                .withProperty("garage.s3.access.key", "configured")
                .withProperty("garage.s3.secret.key", "configured")
                .withProperty("garage.s3.url", "http://garage.internal:3900")
                .withProperty("garage.presigner.url", "https://files.slickhood.com")
                .withProperty("spring.mail.host", "smtp.example.com")
                .withProperty("spring.mail.username", "mailer")
                .withProperty("spring.mail.password", "configured")
                .withProperty("app.public-url", "https://slickhood.com")
                .withProperty("app.cors.allowed-origins", "https://slickhood.com,https://www.slickhood.com")
                .withProperty("wealth.market.enabled", "true")
                .withProperty("wealth.market.alpha-vantage.api-key", "configured")
                .withProperty("wealth.market.alpha-vantage.base-url", "https://www.alphavantage.co")
                .withProperty("wealth.vault.antivirus.enabled", "true")
                .withProperty("wealth.vault.antivirus.required", "true")
                .withProperty("wealth.vault.antivirus.host", "clamav.internal")
                .withProperty("app.insurance.imap.enabled", "true")
                .withProperty("app.insurance.imap.host", "imap.example.com")
                .withProperty("app.insurance.imap.username", "insurance@example.com")
                .withProperty("app.insurance.imap.password", "configured")
                .withProperty("app.insurance.mail.from", "insurance@example.com")
                .withProperty("app.insurance.mail.reply-to", "insurance@example.com")
                .withProperty("affiliate.commission-rate", "10")
                .withProperty("affiliate.minimum-payout", "1000")
                .withProperty("affiliate.commission-hold-days", "14")
                .withProperty("payment.paystack.secret-key", "configured");
    }
}
