package org.pms.silverocean.service.payment.platforms.paystack;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.payment.WebhookSignatureVerifier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaystackWebhookSignatureTest {
    @Test
    void acceptsValidPaystackSignature() throws Exception {
        String body = "{\"event\":\"charge.success\",\"data\":{\"reference\":\"42\"}}";
        String secret = "sk_test_not_a_real_key";
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        StringBuilder signature = new StringBuilder();
        for (byte value : mac.doFinal(body.getBytes(StandardCharsets.UTF_8))) {
            signature.append(String.format("%02x", value));
        }

        assertTrue(WebhookSignatureVerifier.isPaystackSignatureValid(body, signature.toString(), secret));
    }

    @Test
    void rejectsModifiedPayloadAndMissingSecret() {
        assertFalse(WebhookSignatureVerifier.isPaystackSignatureValid("{}", "deadbeef", "secret"));
        assertFalse(WebhookSignatureVerifier.isPaystackSignatureValid("{}", "deadbeef", ""));
    }
}
