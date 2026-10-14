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

    public void reset() {
        transaction.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM reservations");
            jdbc.update("DELETE FROM resources");
        });
    }
}
