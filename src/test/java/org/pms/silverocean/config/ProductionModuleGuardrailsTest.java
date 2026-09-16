package org.pms.silverocean.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

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
        assertThat(assessment.missingOrUnsafeConfiguration())
                .noneMatch(value -> value.startsWith("whatsapp."));
    }

    @Test
    void validatesWhatsAppOnlyWhenTheChannelIsEnabled() {
        var disabled = completeEnvironment().withProperty("whatsapp.enabled", "false")
                .withProperty("whatsapp.api-url", "")
                .withProperty("whatsapp.access-token", "")
                .withProperty("whatsapp.app-secret", "")
                .withProperty("whatsapp.verify-token", "");
        assertThat(new ProductionModuleGuardrails(disabled).assess().ready()).isTrue();

        var enabled = completeEnvironment().withProperty("whatsapp.access-token", "");
        assertThat(new ProductionModuleGuardrails(enabled).assess().missingOrUnsafeConfiguration())
                .contains("whatsapp.access-token (protected value required)");
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

    @Test
    void resolvesProtectedSystemEnvironmentNamesIncludingMarketplaceCategory() {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("whatsapp-test", Map.of(
                "WHATSAPP_API_URL", "https://graph.facebook.com/v22.0/%s/messages",
                "WHATSAPP_TEMPLATES_MARKETPLACE_DELIVERY_APPROVED", "true")));

        assertThat(environment.getProperty("whatsapp.api-url")).isEqualTo("https://graph.facebook.com/v22.0/%s/messages");
        assertThat(environment.getProperty("whatsapp.templates.marketplace_delivery.approved")).isEqualTo("true");
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
                .withProperty("payment.paystack.callback-url", "https://app.slickhood.com/payment/callback")
                .withProperty("whatsapp.enabled", "true")
                .withProperty("whatsapp.api-url", "https://graph.facebook.com/v22.0/%s/messages")
                .withProperty("whatsapp.business-account-id", "2306816990066695")
                .withProperty("whatsapp.phone-number-id", "1220537841150602")
                .withProperty("whatsapp.access-token", "configured")
                .withProperty("whatsapp.app-secret", "configured")
                .withProperty("whatsapp.verify-token", "configured")
                .withProperty("whatsapp.templates.billing.approved", "true")
                .withProperty("whatsapp.templates.billing.name", "slickhood_billing_notification_v1")
                .withProperty("whatsapp.templates.billing.language", "en")
                .withProperty("whatsapp.templates.property.approved", "true")
                .withProperty("whatsapp.templates.property.name", "slickhood_property_notification_v1")
                .withProperty("whatsapp.templates.property.language", "en")
                .withProperty("whatsapp.templates.marketplace_delivery.approved", "true")
                .withProperty("whatsapp.templates.marketplace_delivery.name", "slickhood_marketplace_notification_v1")
                .withProperty("whatsapp.templates.marketplace_delivery.language", "en")
                .withProperty("whatsapp.templates.security.approved", "true")
                .withProperty("whatsapp.templates.security.name", "slickhood_security_notification_v1")
                .withProperty("whatsapp.templates.security.language", "en")
                .withProperty("whatsapp.templates.marketing.approved", "true")
                .withProperty("whatsapp.templates.marketing.name", "slickhood_marketing_notification_v1")
                .withProperty("whatsapp.templates.marketing.language", "en");
    }
}
