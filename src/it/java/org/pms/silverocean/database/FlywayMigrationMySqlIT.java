package org.pms.silverocean.database;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Certifies the real production upgrade boundary on MySQL. The first migration run recreates the
 * production V46 boundary; the second applies this release through V57 and validates checksums.
 */
@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationMySqlIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("slickhood_flyway")
            .withUsername("slickhood_test")
            .withPassword("slickhood_test_only");

    @Test
    void productionV46UpgradesCleanlyThroughV57() throws Exception {
        try (var connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v46-production-schema.sql"));
        }

        Flyway release = configuration()
                .baselineVersion(MigrationVersion.fromVersion("46"))
                .baselineOnMigrate(true)
                .load();
        assertTrue(release.migrate().success);
        release.validate();
        assertEquals("57", release.info().current().getVersion().getVersion());
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration configuration() {
        return Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(true);
    }
}
