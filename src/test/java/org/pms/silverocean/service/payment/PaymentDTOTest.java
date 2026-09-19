package org.pms.silverocean.service.payment.wrappers;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.entities.PMSPayment;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentDTOTest {
    @Test void legacyValuesDoNotBreakPaymentHistory() {
        PMSPayment payment = new PMSPayment();
        payment.setId(7L);
        payment.setChannel("Legacy bank import");
        payment.setCategory("LEGACY_COLLECTION");
        payment.setStatus("success");
        payment.setInProgress(false);

        PaymentDTO result = new PaymentDTO(payment, "Imported payment");

        assertThat(result.channel()).isEqualTo("Legacy bank import");
        assertThat(result.category()).isEqualTo("LEGACY_COLLECTION");
        assertThat(result.success()).isTrue();
    }
}
