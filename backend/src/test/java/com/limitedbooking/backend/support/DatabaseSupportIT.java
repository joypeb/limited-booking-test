package com.limitedbooking.backend.support;

import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.assertThat;

class DatabaseSupportIT {
    @Test void connectsToOwnedPostgresWithoutStartingRedis() throws Exception {
        var support = ContainerTestSupport.postgresOnly();
        try (support; var connection = DriverManager.getConnection(support.postgres.getJdbcUrl(),
                support.postgres.getUsername(), support.postgres.getPassword());
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT current_database()")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo(support.postgres.getDatabaseName());
            assertThat(support.postgres.getMappedPort(5432)).isNotEqualTo(5432);
            assertThat(support.redis.getContainerId()).isNull();
        }
        assertThat(support.postgres.isRunning()).isFalse();
        assertThat(support.redis.getContainerId()).isNull();
    }
}
