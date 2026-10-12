package org.pms.silverocean.database.pms;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SokoBuyerIdentityMigrationContractTest {

    @Test
    void migrationSnapshotsOnlyTheOrderingBuyerAndSeedsAReusableBuyerRole() throws Exception {
        try (InputStream input=getClass().getResourceAsStream(
                "/db/migration/V131__snapshot_soko_buyer_identity.sql")) {
            assertThat(input).isNotNull();
            String migration=new String(input.readAllBytes(),StandardCharsets.UTF_8);

            assertThat(migration)
                    .contains("customer_name_snapshot VARCHAR(160)")
                    .contains("customer_email_snapshot VARCHAR(255)")
                    .contains("LEFT JOIN pms_users u ON u.id = o.customer_user_id")
                    .contains("'MarketplaceBuyer'")
                    .contains("WHERE NOT EXISTS (SELECT 1 FROM pms_role WHERE name = 'MarketplaceBuyer')")
                    .contains("self_assignable = 1")
                    .contains("UPDATE pms_soko_product")
                    .contains("UPPER(TRIM(status)) = 'REJECTED'")
                    .doesNotContain("owner_user_id")
                    .doesNotContain("JOIN pms_soko_store");
        }
    }
}
