package org.pms.silverocean.service.payment.platforms.flutterwave;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PMSPayment;
import org.pms.silverocean.service.payment.platforms.flutterwave.wrappers.FWChargeCompletedData;
import org.pms.silverocean.service.payment.platforms.flutterwave.wrappers.FWVerifyTransactionDTO;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FWVerifyTransactionTest {
    private final PMSPayment payment = payment();
    private final PMSInvoice invoice = invoice();

    @Test
    void acceptsOnlyAnExactServerVerifiedTransaction() {
        assertTrue(FWVerifyTransaction.isVerifiedTransaction(
                payment, invoice, response(991L, "42", 300.00, "KES", "successful")));
    }

    @Test
    void rejectsWrongLocalReference() {
        assertFalse(FWVerifyTransaction.isVerifiedTransaction(
                payment, invoice, response(991L, "43", 300.00, "KES", "successful")));
    }

    @Test
    void rejectsWrongProviderReferenceAmountCurrencyOrStatus() {
        assertFalse(FWVerifyTransaction.isVerifiedTransaction(
                payment, invoice, response(992L, "42", 300.00, "KES", "successful")));
        assertFalse(FWVerifyTransaction.isVerifiedTransaction(
                payment, invoice, response(991L, "42", 299.99, "KES", "successful")));
        assertFalse(FWVerifyTransaction.isVerifiedTransaction(
                payment, invoice, response(991L, "42", 300.00, "USD", "successful")));
        assertFalse(FWVerifyTransaction.isVerifiedTransaction(
                payment, invoice, response(991L, "42", 300.00, "KES", "failed")));
    }

    private static PMSPayment payment() {
        PMSPayment payment = new PMSPayment();
        payment.setId(42L);
        payment.setAmount(300.00);
        payment.setThirdPartyTransId("991");
        return payment;
    }

    private static PMSInvoice invoice() {
        PMSInvoice invoice = new PMSInvoice();
        invoice.setCurrency("KES");
        return invoice;
    }

    private static FWVerifyTransactionDTO response(long id, String txRef, double amount,
                                                    String currency, String status) {
        FWChargeCompletedData data = new FWChargeCompletedData(
                id, txRef, "FLW-REF", null, amount, currency, amount,
                0, 0, "Approved. Successful", null, null, null, status,
                "card", null, 0, null, null);
        return new FWVerifyTransactionDTO("success", "ok", data);
    }
}
