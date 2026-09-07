package org.pms.silverocean.service.payment;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.entities.PMSPayment;
import org.pms.silverocean.service.payment.wrappers.ManualPaymentDTO;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class ManualPaymentReceiptStateTest {
    @Test
    void recordedManualPaymentIsCompleteAndEligibleForReceipt() {
        PMSPayment payment = new PMSPayment(new ManualPaymentDTO("TEST-INVOICE", 100, "Bank", "TEST-RECEIPT", LocalDate.now()));
        assertThat(payment.isInProgress()).isFalse();
        assertThat(payment.isCompletedSuccessfully()).isTrue();
    }
}
