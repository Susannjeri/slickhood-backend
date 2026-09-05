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
 * faithful production test. The release runner imports a data-free schema from the deployed V67 database,
 * then this test baselines that non-empty database at V67 and applies every pending migration.
 */
@EnabledIf("externalMysqlAvailable")
class ProductionBaselineMigrationMySqlIT {
    private static final String URL = setting("SLICKHOOD_TEST_MYSQL_URL");
    private static final String USERNAME = setting("SLICKHOOD_TEST_MYSQL_USERNAME");
    private static final String PASSWORD = setting("SLICKHOOD_TEST_MYSQL_PASSWORD");

    static boolean externalMysqlAvailable() {
        return URL != null && !URL.isBlank();
    }

    private static String setting(String name) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? System.getenv(name) : value;
    }

    @Test
    void productionV67SchemaMigratesCleanlyToTheCandidateVersion() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(URL, USERNAME, PASSWORD)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("67"))
                .baselineDescription("deployed-production-v67")
                .load();

        flyway.migrate();
        flyway.validate();

        MigrationInfo current = flyway.info().current();
        assertEquals("70", current.getVersion().getVersion());
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
