package org.pms.silverocean.database.pms;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceFinanceOperationMigrationContractTest {
    @Test
    void migrationCreatesTheAppendOnlyUniqueLedgerAndBackfillsBothOperationTypes() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/V128__services_finance_operation_ledger.sql")) {
            assertThat(input).isNotNull();
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(migration)
                    .contains("CREATE TABLE pms_sp_finance_operation")
                    .contains("uk_sp_finance_provider_channel_type_ref UNIQUE")
                    .contains("provider_user_id, payment_channel, operation_type, provider_reference")
                    .contains("operation_status CHECK (operation_status = 'CONFIRMED')")
                    .contains("'REFUND', 'CONFIRMED'")
                    .contains("'SETTLEMENT', 'CONFIRMED'")
                    .contains("INSERT IGNORE INTO pms_sp_finance_operation");
        }
    }
}
