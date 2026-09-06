package org.pms.silverocean.database;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Release-only migration rehearsal against a disposable copy of the schema at the currently deployed
 * production version. SlickHood's core schema predates Flyway, so an empty-schema migration is not a
 * faithful production test. The release runner imports a data-free schema from the deployed database,
 * then this test baselines that non-empty database at the configured deployed version and applies
 * every pending migration. The reset flag is deliberately separate from the connection settings so
 * merely supplying a production JDBC URL can never drop its Flyway history table.
 */
@EnabledIf("externalMysqlAvailable")
class ProductionBaselineMigrationMySqlIT {
    private static final String URL = setting("SLICKHOOD_TEST_MYSQL_URL");
    private static final String USERNAME = setting("SLICKHOOD_TEST_MYSQL_USERNAME");
    private static final String PASSWORD = setting("SLICKHOOD_TEST_MYSQL_PASSWORD");
    private static final String BASELINE_VERSION = settingOrDefault(
            "SLICKHOOD_TEST_MYSQL_BASELINE_VERSION", "67");
    private static final String EXPECTED_VERSION = settingOrDefault(
            "SLICKHOOD_EXPECTED_FLYWAY_VERSION", "72");

    static boolean externalMysqlAvailable() {
        return URL != null
                && !URL.isBlank()
                && Boolean.parseBoolean(setting("SLICKHOOD_TEST_MYSQL_ALLOW_RESET"));
    }

    private static String setting(String name) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? System.getenv(name) : value;
    }

    private static String settingOrDefault(String name, String fallback) {
        String value = setting(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    @Test
    void productionBaselineSchemaMigratesCleanlyToTheCandidateVersion() throws Exception {
        if (!URL.matches("jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/slickhood_rehearsal_[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Migration rehearsal requires an explicitly named loopback disposable database");
        }
        // The release rehearsal imports schema only, so Flyway's production history table exists
        // without its rows. Remove that empty copy and let Flyway create an explicit baseline;
        // otherwise it incorrectly attempts V1 against an already-populated production schema.
        try (var connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
             var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("select count(*) from flyway_schema_history")) {
                rows.next();
                assertEquals(0, rows.getLong(1), "Refusing to reset non-empty Flyway history; import schema only");
            }
            statement.execute("drop table if exists flyway_schema_history");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(URL, USERNAME, PASSWORD)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion(BASELINE_VERSION))
                .baselineDescription("deployed-production-v" + BASELINE_VERSION)
                .load();

        flyway.migrate();
        flyway.validate();

        MigrationInfo current = flyway.info().current();
        assertEquals(EXPECTED_VERSION, current.getVersion().getVersion());
        assertFalse(flyway.info().pending().length > 0, "all candidate migrations must be applied");

        try (var connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery(
                    "select count(*) from flyway_schema_history where success = 0")) {
                result.next();
                assertEquals(0, result.getInt(1), "Flyway must not leave failed migration records");
            }
        }
    }
}
