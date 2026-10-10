package org.pms.silverocean.database.pms;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SokoRepositoryContractTest {

    @Test
    void checkoutAndInventoryMutationQueriesUsePessimisticWriteLocks() throws Exception {
        assertThat(SokoStoreRepo.class.getMethod("findByIdForCheckout", long.class).getAnnotation(Lock.class).value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(SokoOrderRepo.class.getMethod("findPendingCheckoutForUpdate", long.class, long.class, Pageable.class).getAnnotation(Lock.class).value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(SokoOrderRepo.class.getMethod("findByInvoiceRefAndActiveTrue",String.class).getAnnotation(Lock.class).value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(SokoProductRepo.class.getMethod("findByIdForUpdate", long.class).getAnnotation(Lock.class).value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void refundedAndReversedOrdersDoNotKeepProductsLockedForEditing() throws Exception {
        String query=SokoOrderItemRepo.class.getMethod("countOpenOrdersForProduct", long.class)
                .getAnnotation(Query.class).value();

        assertThat(query).contains("'REFUNDED'", "'PAYMENT_REVERSED'");
    }

    @Test
    void productionSchemaIndexesTheSerializedBuyerStorePendingLookup() throws Exception {
        try(InputStream input=getClass().getResourceAsStream("/db/migration/V122__serialize_soko_buyer_store_checkouts.sql")){
            assertThat(input).isNotNull();
            String migration=new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(migration).contains("customer_user_id, store_id, status, active");
        }
    }

    @Test
    void productionSchemaSeparatesFinanceSnapshotsAndDeduplicatesProviderOperations() throws Exception {
        try(InputStream input=getClass().getResourceAsStream("/db/migration/V123__separate_soko_finance_snapshots.sql")){
            assertThat(input).isNotNull();
            String migration=new String(input.readAllBytes(),StandardCharsets.UTF_8);
            assertThat(migration).contains("refund_requested_amount DECIMAL(19,2)","reversed_amount DECIMAL(19,2)","charged_back_amount DECIMAL(19,2)");
        }
        try(InputStream input=getClass().getResourceAsStream("/db/migration/V124__deduplicate_soko_finance_operations.sql")){
            assertThat(input).isNotNull();
            String migration=new String(input.readAllBytes(),StandardCharsets.UTF_8);
            assertThat(migration).contains("provider_reference VARCHAR(120)","UNIQUE (order_id, provider_reference)","amount DECIMAL(19,2)");
        }
    }

    @Test
    void riderAssignmentLookupCanExcludeReleasedPackedOrders() throws Exception {
        assertThat(SokoOrderRepo.class.getMethod("findAllByRiderIdInAndStatusNotInAndActiveTrue",List.class,List.class,Pageable.class))
                .isNotNull();
    }

    @Test
    void productionSchemaEnforcesCheckoutIdempotencyAcrossDifferentStoreLocks() throws Exception {
        try(InputStream input=getClass().getResourceAsStream("/db/migration/V50__soko_services_release_guardrails.sql")){
            assertThat(input).isNotNull();
            String migration=new String(input.readAllBytes(),StandardCharsets.UTF_8);
            assertThat(migration).contains("ADD UNIQUE KEY uk_soko_checkout_idempotency (customer_user_id, checkout_idempotency_key)");
        }
    }
}
