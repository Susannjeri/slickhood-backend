package org.pms.silverocean.database.pms;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.util.Arrays;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class PMSInvoiceRepoMarketplaceQueryTest {
    @Test
    void marketplaceDestinationLookupOnlyReturnsSourcesThatCanStillStartPayment() {
        Query query=Arrays.stream(PMSInvoiceRepo.class.getDeclaredMethods())
                .filter(method->method.getName().equals("findMarketplaceSourcePaymentAccountId"))
                .findFirst().orElseThrow().getAnnotation(Query.class);
        String sql=query.value().replaceAll("\\s+"," ").toUpperCase(Locale.ROOT);

        assertThat(sql)
                .contains("O.STATUS = 'PENDING_PAYMENT'")
                .contains("O.PAYMENT_STATUS IN ('UNPAID', 'PARTIALLY_PAID')")
                .contains("O.STOCK_RELEASED = 0")
                .contains("O.RESERVATION_EXPIRES_AT > UTC_TIMESTAMP(6)")
                .contains("B.STATUS = 'AWAITING_PAYMENT'")
                .contains("B.PAYMENT_STATUS = 'UNPAID'")
                .contains("O.CUSTOMER_USER_ID = I.BILLED_USER_ID")
                .contains("S.OWNER_USER_ID = I.PAY_TO_USER_ID")
                .contains("B.CREATED_BY = I.BILLED_USER_ID")
                .contains("P.USER_ID = I.PAY_TO_USER_ID")
                .contains("A.CATEGORY = 'MERCHANT'")
                .contains("A.CREATED_BY = I.PAY_TO_USER_ID")
                .contains("O.PAYMENT_CHANNEL IS NULL OR O.PAYMENT_CHANNEL = A.CHANNEL")
                .contains("B.PAYMENT_CHANNEL IS NULL OR B.PAYMENT_CHANNEL = A.CHANNEL");
    }
}
