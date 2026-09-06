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
                "affiliate.commission-rate (must be explicit)", "helpdesk.ai.enabled=true",
                "helpdesk.ai.api-key / OPENAI_API_KEY", "one verified Paystack or M-Pesa callback secret",
                "kyc.ocr.provider=aws-textract", "garage.s3.require-https=true",
                "garage.bootstrap.enabled=false");
    }

    @Test
    void acceptsCompleteProductionConfiguration() {
        var environment = completeEnvironment();
        var guardrails = new ProductionModuleGuardrails(environment);
        guardrails.validateSafeBounds();

        assertThat(guardrails.assess().ready()).isTrue();
    }

    @Test
    void acceptsAwsSdkEndpointResolutionAndInsuranceEnvironmentAliases() {
        var environment = completeEnvironment()
                .withProperty("garage.s3.url", "")
                .withProperty("garage.presigner.url", "")
                .withProperty("app.insurance.imap.enabled", "")
                .withProperty("app.insurance.imap.host", "")
                .withProperty("app.insurance.imap.username", "")
                .withProperty("app.insurance.imap.password", "")
                .withProperty("app.insurance.mail.from", "")
                .withProperty("app.insurance.mail.reply-to", "")
                .withProperty("INSURANCE_IMAP_ENABLED", "true")
                .withProperty("INSURANCE_IMAP_HOST", "imap.example.com")
                .withProperty("INSURANCE_IMAP_USERNAME", "insurance@example.com")
                .withProperty("INSURANCE_IMAP_PASSWORD", "configured")
                .withProperty("INSURANCE_IMAP_SSL", "true")
                .withProperty("INSURANCE_MAIL_FROM", "insurance@example.com")
                .withProperty("INSURANCE_REPLY_TO", "insurance@example.com");

        assertThat(new ProductionModuleGuardrails(environment).assess().ready()).isTrue();
    }

    @Test
    void acceptsExplicitAwsDefaultCredentialChainWithoutStaticSecrets() {
        var environment = completeEnvironment()
                .withProperty("garage.s3.access.key", "")
                .withProperty("garage.s3.secret.key", "")
                .withProperty("garage.s3.use-default-credentials", "true");

        assertThat(new ProductionModuleGuardrails(environment).assess().ready()).isTrue();
    }

    @Test
    void rejectsAmbiguousAwsCredentialSources() {
        var environment = completeEnvironment()
                .withProperty("garage.s3.use-default-credentials", "true");

        assertThat(new ProductionModuleGuardrails(environment).assess().missingOrUnsafeConfiguration())
                .contains("exactly one S3 credential source: static key pair or garage.s3.use-default-credentials=true");
    }

    @Test
    void requiresTheProductionFrontendInTheHttpsCorsAllowlist() {
        var environment = completeEnvironment()
                .withProperty("app.cors.allowed-origins", "https://slickhood.com");

        assertThat(new ProductionModuleGuardrails(environment).assess().missingOrUnsafeConfiguration())
                .contains("app.cors.allowed-origins must include https://app.slickhood.com");
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
                .withProperty("garage.s3.bucket", "slickhood-production-documents")
                .withProperty("garage.s3.region", "ca-central-1")
                .withProperty("garage.s3.require-https", "true")
                .withProperty("garage.bootstrap.enabled", "false")
                .withProperty("garage.presigner.duration-seconds", "120")
                .withProperty("garage.s3.url", "https://garage.internal:3900")
                .withProperty("garage.presigner.url", "https://files.slickhood.com")
                .withProperty("kyc.ocr.provider", "aws-textract")
                .withProperty("kyc.ocr.aws.region", "ca-central-1")
                .withProperty("spring.mail.host", "smtp.example.com")
                .withProperty("spring.mail.username", "mailer")
                .withProperty("spring.mail.password", "configured")
                .withProperty("app.public-url", "https://slickhood.com")
                .withProperty("app.cors.allowed-origins", "https://app.slickhood.com,https://slickhood.com")
                .withProperty("wealth.market.enabled", "true")
                .withProperty("wealth.market.alpha-vantage.api-key", "configured")
                .withProperty("wealth.market.alpha-vantage.base-url", "https://www.alphavantage.co")
                .withProperty("wealth.vault.antivirus.enabled", "true")
                .withProperty("wealth.vault.antivirus.required", "true")
                .withProperty("wealth.vault.antivirus.host", "clamav.internal")
                .withProperty("app.insurance.imap.enabled", "true")
                .withProperty("app.insurance.imap.ssl", "true")
                .withProperty("app.insurance.imap.host", "imap.example.com")
                .withProperty("app.insurance.imap.username", "insurance@example.com")
                .withProperty("app.insurance.imap.password", "configured")
                .withProperty("app.insurance.mail.from", "insurance@example.com")
                .withProperty("app.insurance.mail.reply-to", "insurance@example.com")
                .withProperty("helpdesk.ai.enabled", "true")
                .withProperty("helpdesk.ai.api-key", "configured")
                .withProperty("helpdesk.ai.base-url", "https://api.openai.com/v1")
                .withProperty("affiliate.commission-rate", "10")
                .withProperty("affiliate.minimum-payout", "1000")
                .withProperty("affiliate.commission-hold-days", "14")
                .withProperty("affiliate.eligible-payment-count", "3")
                .withProperty("payment.paystack.secret-key", "configured")
                .withProperty("payment.paystack.enabled", "true")
                .withProperty("payment.paystack.api-url", "https://api.paystack.co")
                .withProperty("payment.paystack.callback-url", "https://app.slickhood.com/payment/callback");
    }
}
