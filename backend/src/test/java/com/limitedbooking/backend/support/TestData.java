package com.limitedbooking.backend.support;

import com.limitedbooking.backend.reservation.StoredReservation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/** 호출자가 전달한 테스트 소유 DataSource에서만 준비하고 초기화한다. */
public final class TestData {
    public static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID USER_A_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID USER_B_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    public static final String ADMIN_USERNAME = "ADMIN";
    public static final String USER_A_USERNAME = "USER_A";
    public static final String USER_B_USERNAME = "USER_B";
    public static final String ADMIN_PASSWORD = "test-only-admin-password";
    public static final String USER_A_PASSWORD = "test-only-user-a-password";
    public static final String USER_B_PASSWORD = "test-only-user-b-password";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public TestData(DataSource dataSource) {
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void resource(UUID id, String name, int capacity, int available, Instant createdAt) {
        transaction.executeWithoutResult(status -> jdbc.update("""
                INSERT INTO resources(id, name, capacity, available, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, id, name, capacity, available, createdAt.atOffset(ZoneOffset.UTC)));
    }

    public void reservation(StoredReservation reservation) {
        var cancelledAt = reservation.cancelledAt();
        transaction.executeWithoutResult(status -> jdbc.update("""
                INSERT INTO reservations(id, resource_id, user_id, idempotency_key, status, created_at, cancelled_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, reservation.id(), reservation.resourceId(), reservation.userId(),
                reservation.idempotencyKey(), reservation.status().name(),
                reservation.createdAt().atOffset(ZoneOffset.UTC),
                cancelledAt == null ? null : cancelledAt.atOffset(ZoneOffset.UTC)));
    }

    public long resourceCount() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM resources", Long.class);
        return count == null ? 0L : count;
    }

    public void reset() {
        transaction.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM reservations");
            jdbc.update("DELETE FROM resources");
        });
    }
}
