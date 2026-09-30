package org.pms.silverocean.service.payment.platforms.paystack;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaystackEventRedactionTest {
    @Test
    void initializationAuditExcludesCheckoutUrlAndAccessCode() {
        var response = new PaystackPlatform.PaystackInitializeResponse(true, "Authorization URL created",
                new PaystackPlatform.PaystackInitializeData(
                        "https://checkout.paystack.example/private-session",
                        "access-code-secret",
                        "218"));

        String persistedEvent = response.auditEvent().toString();

        assertThat(persistedEvent)
                .contains("status=true", "reference=218")
                .doesNotContain("private-session", "access-code-secret", "authorizationUrl", "accessCode");
    }

    @Test
    void initializationAuditHandlesProviderResponseWithoutData() {
        var response = new PaystackPlatform.PaystackInitializeResponse(false, "Unable to initialize", null);

        assertThat(response.auditEvent().toString())
                .contains("status=false", "message=Unable to initialize", "reference=null");
    }
}
