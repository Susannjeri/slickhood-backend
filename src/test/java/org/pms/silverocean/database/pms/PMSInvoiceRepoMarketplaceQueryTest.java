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

    @Test
    void batchMarketplaceLookupUsesTheSamePayableSourceBoundaryForEveryInvoiceRef() {
        Query query=Arrays.stream(PMSInvoiceRepo.class.getDeclaredMethods())
                .filter(method->method.getName().equals("findPayableMarketplaceInvoiceRefs"))
                .findFirst().orElseThrow().getAnnotation(Query.class);
        String sql=query.value().replaceAll("\\s+"," ").toUpperCase(Locale.ROOT);

        assertThat(sql)
                .contains("O.INVOICE_REF IN (:INVOICEREFS)")
                .contains("B.INVOICE_REF IN (:INVOICEREFS)")
                .contains("I.PAYMENT_ACCOUNT_ID = O.PAYMENT_ACCOUNT_ID")
                .contains("I.PAYMENT_ACCOUNT_ID = B.PAYMENT_ACCOUNT_ID")
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
                .contains("A.ACTIVE = 1")
                .contains("A.VERIFIED = 1")
                .contains("A.CATEGORY = 'MERCHANT'")
                .contains("A.CREATED_BY = I.PAY_TO_USER_ID")
                .contains("O.PAYMENT_CHANNEL IS NULL OR O.PAYMENT_CHANNEL = A.CHANNEL")
                .contains("B.PAYMENT_CHANNEL IS NULL OR B.PAYMENT_CHANNEL = A.CHANNEL");
        assertThat(countOccurrences(sql, ":INVOICEREFS")).isEqualTo(2);
    }

    private static int countOccurrences(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
