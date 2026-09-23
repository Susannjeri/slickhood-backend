package org.pms.silverocean.service.payment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentPhoneNumberTest {
    @Test
    void normalizesSupportedKenyanMobileFormats() {
        assertEquals("254712345678", PaymentPhoneNumber.normalizeKenyanMsisdn("+254 712 345 678"));
        assertEquals("254712345678", PaymentPhoneNumber.normalizeKenyanMsisdn("0712345678"));
        assertEquals("254112345678", PaymentPhoneNumber.normalizeKenyanMsisdn("112345678"));
    }

    @Test
    void rejectsNonKenyanAndMalformedNumbers() {
        assertThrows(PaymentRequestException.class,
                () -> PaymentPhoneNumber.normalizeKenyanMsisdn("+256712345678"));
        assertThrows(PaymentRequestException.class,
                () -> PaymentPhoneNumber.normalizeKenyanMsisdn("254123"));
    }
}
