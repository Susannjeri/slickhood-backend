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

    @Test
    void variablePricingAndCatalogueMigrationPreservesCommercialAndKycHistory() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/V130__services_catalogue_publish_and_variable_pricing.sql")) {
            assertThat(input).isNotNull();
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(migration)
                    .contains("pricing_unit_rate DECIMAL(19,2)")
                    .contains("unit_quantity DECIMAL(19,4)")
                    .contains("scheduled_end_at DATETIME(6)")
                    .contains("MODIFY COLUMN description VARCHAR(1000)")
                    .contains("'Plumbing services'")
                    .contains("'Chef services'")
                    .contains("'Catering services'")
                    .contains("INSERT INTO pms_sp_category_doc_types")
                    .contains("INSERT INTO pms_kyc_matrix_requirement")
                    .contains("condition_rule, condition_value")
                    .contains("WHEN 'MANDATORY' THEN 3")
                    .contains("UPDATE pms_kyc_matrix_requirement canonical")
                    .contains("JOIN pms_kyc_matrix_requirement strongest ON strongest.id = picked.strongest_id")
                    .contains("canonical.active = GREATEST(canonical.active, strongest.active)")
                    .contains("canonical.obligation = strongest.obligation")
                    .contains("canonical.condition_rule = strongest.condition_rule")
                    .contains("CHAR_LENGTH(TRIM(service.description)) < 20")
                    .doesNotContain("INSERT IGNORE INTO pms_sp_category_doc_types");
        }
    }
}
