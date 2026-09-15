package org.pms.silverocean.database;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Rehearse the additive role seed on an isolated MySQL 8 instance, never customer data. */
@Testcontainers(disabledWithoutDocker = true)
class SharedSecurityRoleMigrationMySqlIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Test
    void migrationAddsOnlyMissingRolesAndPreservesDisabledDefinitionsOnRerun() throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE pms_team_role_definition (id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid BINARY(16) UNIQUE NOT NULL, created_on DATETIME(6), active BIT NOT NULL, created_by BIGINT, last_modified_date DATETIME(6), code VARCHAR(80) UNIQUE NOT NULL, display_name VARCHAR(120) NOT NULL, description VARCHAR(300), business_area VARCHAR(40) NOT NULL, permission_template VARCHAR(40) NOT NULL)");
            statement.execute("INSERT INTO pms_team_role_definition (uuid, active, code, display_name, business_area, permission_template) VALUES (UUID_TO_BIN(UUID()), 0, 'CUSTOM_RENTAL_GUARD', 'Existing disabled guard', 'LANDLORD', 'GUARD'), (UUID_TO_BIN(UUID()), 1, 'ESTATE_GUARD', 'Estate guard', 'ESTATE_MANAGEMENT', 'GUARD')");
            String sql;
            try (var resource = new ClassPathResource("db/migration/V83__shared_customer_security_team_roles.sql").getInputStream()) {
                sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertEquals(3, statement.executeUpdate(sql));
            assertEquals(0, statement.executeUpdate(sql), "Rerun must never duplicate a role");
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM pms_team_role_definition")) {
                rows.next(); assertEquals(5, rows.getInt(1));
            }
            try (var rows = statement.executeQuery("SELECT active, display_name FROM pms_team_role_definition WHERE code = 'CUSTOM_RENTAL_GUARD'")) {
                rows.next(); assertEquals(0, rows.getInt(1)); assertEquals("Existing disabled guard", rows.getString(2));
            }
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM pms_team_role_definition WHERE code IN ('LANDLORD_SECURITY_SUPERVISOR', 'SALE_GUARD', 'SALE_SECURITY_SUPERVISOR') AND active = 1")) {
                rows.next(); assertEquals(3, rows.getInt(1));
            }
        }
    }
}
