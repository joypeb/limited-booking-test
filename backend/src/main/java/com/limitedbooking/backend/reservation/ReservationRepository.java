package com.limitedbooking.backend.reservation;

import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReservationRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ReservationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public StoredReservation insert(UUID id, UUID resourceId, UUID userId, IdempotencyKey key, java.time.Instant createdAt) {
        var rows = jdbc.query("""
                INSERT INTO reservations(id, resource_id, user_id, idempotency_key, status, created_at, cancelled_at)
                VALUES (:id, :resourceId, :userId, :key, 'CONFIRMED', :createdAt, NULL)
                RETURNING id, resource_id, user_id, idempotency_key, status, created_at, cancelled_at
                """, Map.of("id", id, "resourceId", resourceId, "userId", userId,
                "key", key.value(), "createdAt", createdAt.atOffset(java.time.ZoneOffset.UTC)), ROW_MAPPER);
        return DataAccessUtils.requiredSingleResult(rows);
    }

    public Optional<StoredReservation> findByUserAndKey(UUID userId, String key) {
        var rows = jdbc.query("""
                SELECT id, resource_id, user_id, idempotency_key, status, created_at, cancelled_at
                FROM reservations
                WHERE user_id = :userId AND idempotency_key = :key
                """, Map.of("userId", userId, "key", key), ROW_MAPPER);
        return Optional.ofNullable(DataAccessUtils.singleResult(rows));
    }
    public Optional<StoredReservation> findByIdAndUser(UUID id, UUID userId) {
        return Optional.ofNullable(DataAccessUtils.singleResult(jdbc.query("""
                SELECT id, resource_id, user_id, idempotency_key, status, created_at, cancelled_at
                FROM reservations WHERE id = :id AND user_id = :userId
                """, Map.of("id", id, "userId", userId), ROW_MAPPER)));
    }

    public int markCancelled(UUID id, UUID userId, java.time.Instant cancelledAt) {
        return jdbc.update("""
                UPDATE reservations SET status = 'CANCELLED', cancelled_at = :now
                WHERE id = :id AND user_id = :userId AND status = 'CONFIRMED'
                """, Map.of("id", id, "userId", userId, "now", cancelledAt.atOffset(java.time.ZoneOffset.UTC)));
    }

    private static final org.springframework.jdbc.core.RowMapper<StoredReservation> ROW_MAPPER = (rs, n) -> {
        var cancelledAt = rs.getObject("cancelled_at", OffsetDateTime.class);
        return new StoredReservation(rs.getObject("id", UUID.class), rs.getObject("resource_id", UUID.class),
                rs.getObject("user_id", UUID.class), rs.getString("idempotency_key"),
                ReservationStatus.valueOf(rs.getString("status")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                cancelledAt == null ? null : cancelledAt.toInstant());
    };

}
