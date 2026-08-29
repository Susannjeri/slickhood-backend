package org.pms.silverocean.config;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.SQLException;

@Slf4j
public class CustomHikariDatasource extends HikariDataSource {

    @Override
    public Connection getConnection() throws SQLException {
        try {
            return super.getConnection();
        } catch (SQLException e) {
            logPoolMetrics(); // Log pool metrics on exception
            throw e;
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        try {
            return super.getConnection(username, password);
        } catch (SQLException e) {
            logPoolMetrics(); // Log pool metrics on exception
            throw e;
        }
    }

    private void logPoolMetrics() {
        HikariPoolMXBean pool = this.getHikariPoolMXBean();
        if (pool == null) {
            log.warn("HikariCP pool metrics are unavailable because the pool has not started");
            return;
        }
        log.info("=== HikariCP Pool Metrics ===");
        log.info("Active Connections: {}", pool.getActiveConnections());
        log.info("Idle Connections: {}", pool.getIdleConnections());
        log.info("Total Connections: {}", pool.getTotalConnections());
        log.info("Threads Awaiting Connection: {}", pool.getThreadsAwaitingConnection());
        log.info("=============================");
    }
}
