package com.limitedbooking.backend.common;

import com.limitedbooking.backend.support.ContainerTestSupport;
import io.lettuce.core.RedisClient;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ToolchainSmokeIT {
    @Test
    void postgresMigrationAndInsertSelectReturnStoredValue() throws Exception {
        try (var resources = new ContainerTestSupport()) {
            var pg = resources.postgres;
            var flyway = Flyway.configure().dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                    .locations("classpath:db/toolchain").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
            try (var connection = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())) {
                var id = UUID.randomUUID();
                try (var insert = connection.prepareStatement("INSERT INTO toolchain_probe(id,value) VALUES (?,?)")) {
                    insert.setObject(1, id); insert.setInt(2, 73);
                    assertThat(insert.executeUpdate()).isEqualTo(1);
                }
                try (var select = connection.prepareStatement("SELECT value FROM toolchain_probe WHERE id=?")) {
                    select.setObject(1,id);
                    try (var rows = select.executeQuery()) {
                        assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isEqualTo(73);
                        assertThat(rows.next()).isFalse();
                    }
                }
            }
        }
    }

    @Test
    void redisSetGetReturnsStoredValue() {
        try (var resources = new ContainerTestSupport()) {
            var redis = resources.redis;
            var client = RedisClient.create("redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
            try (var connection = client.connect()) {
                var key = "toolchain:smoke:" + UUID.randomUUID();
                var commands = connection.sync();
                assertThat(commands.set(key, "73")).isEqualTo("OK");
                assertThat(commands.get(key)).isEqualTo("73");
                assertThat(commands.del(key)).isEqualTo(1);
            } finally { client.shutdown(java.time.Duration.ZERO, java.time.Duration.ofSeconds(10)); }
        }
    }

    @Test
    void failedMigrationIsRecordedAndRecoveredInNewIsolatedDatabase() throws Exception {
        String failedContainer;
        var failed = new ContainerTestSupport();
        failedContainer = failed.postgres.getContainerId();
        try (failed) {
            var pg = failed.postgres;
            var flyway = Flyway.configure().dataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword())
                    .locations("classpath:db/toolchain-failure").load();
            assertThatThrownBy(flyway::migrate).isInstanceOf(org.flywaydb.core.api.FlywayException.class);
            try (var c = DriverManager.getConnection(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword());
                 var s = c.createStatement(); var r = s.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success")) {
                assertThat(r.next()).isTrue(); assertThat(r.getInt(1)).isZero();
                System.out.printf("migration-failure run=%s container=%s db=%s metadata=readable applied=0%n",
                        UUID.randomUUID(),failedContainer,pg.getDatabaseName());
            }
        }
        assertThat(failed.postgres.isRunning()).isFalse();
        try (var fresh = new ContainerTestSupport()) {
            assertThat(fresh.postgres.getContainerId()).isNotEqualTo(failedContainer);
            var pg = fresh.postgres;
            var flyway = Flyway.configure().dataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword())
                    .locations("classpath:db/toolchain").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
            try (var c = DriverManager.getConnection(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword());
                 var s = c.createStatement()) {
                assertThat(s.executeUpdate("INSERT INTO toolchain_probe VALUES ('00000000-0000-0000-0000-000000000073',73)")).isEqualTo(1);
                try (var r=s.executeQuery("SELECT value FROM toolchain_probe")) {
                    assertThat(r.next()).isTrue();assertThat(r.getInt(1)).isEqualTo(73);
                }
            }
            System.out.printf("migration-recovery container=%s new-db=%s applied=1%n",pg.getContainerId(),pg.getDatabaseName());
        }
    }
}
