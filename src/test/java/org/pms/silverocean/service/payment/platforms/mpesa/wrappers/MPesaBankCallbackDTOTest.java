package org.pms.silverocean.service.payment.platforms.mpesa.wrappers;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MPesaBankCallbackDTOTest {
    @Test
    void preservesMpesaIdentityAndBankAuditReference() {
        MPesaBankCallbackDTO bankCallback = new MPesaBankCallbackDTO(
                "UCMEWA74WV", "BANK-12345", "INV-300",
                new BigDecimal("300.00"), "20260826205000",
                "12345", "254700000001", "Neo Gulf Logistics");

        MPesaPaymentDTO payment = bankCallback.toMPesaPaymentDTO();

        assertEquals("UCMEWA74WV", payment.transId());
        assertEquals("BANK-12345", payment.thirdPartyTransId());
        assertEquals("INV-300", payment.billRefNumber());
        assertEquals("300.00", payment.transAmount());
        assertEquals("20260826205000", payment.transTime());
    }
}
