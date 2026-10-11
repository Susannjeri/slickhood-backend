package org.pms.silverocean.database.pms;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ServicesMarketplaceMigrationContractTest {

    @Test
    void migrationScrubsLegacyPrivateWarningsAndHoldsHistoricalUpheldPayments() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/V126__rich_services_catalogue_and_soko_boundary.sql")) {
            assertThat(input).isNotNull();
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(migration)
                    .contains("SUBSTRING_INDEX(admin_notes, ' | Warning: ', 1)")
                    .contains("complaint.status = 'RESOLVED'")
                    .contains("complaint.resolution = 'UPHELD'")
                    .contains("SET booking.refund_status = 'REQUESTED'")
                    .contains("NOT IN ('REQUESTED', 'PROCESSING', 'CONFIRMED')");
        }
    }

    @Test
    void bookingIdentityMigrationSnapshotsHistoricalCatalogueAndProviderDetails() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/V129__snapshot_services_booking_identity.sql")) {
            assertThat(input).isNotNull();
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(migration)
                    .contains("service_title_snapshot")
                    .contains("service_category_snapshot")
                    .contains("service_description_snapshot")
                    .contains("provider_business_name_snapshot")
                    .contains("JOIN pms_sp_service service ON service.id = booking.service_id")
                    .contains("LEFT JOIN pms_sp_profile profile ON profile.id = service.profile_id");
        }
    }
}
