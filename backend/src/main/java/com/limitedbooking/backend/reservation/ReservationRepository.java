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

    public Optional<StoredReservation> findByUserAndKey(UUID userId, String key) {
        var rows = jdbc.query("""
                SELECT id, resource_id, user_id, idempotency_key, status, created_at, cancelled_at
                FROM reservations
                WHERE user_id = :userId AND idempotency_key = :key
                """, Map.of("userId", userId, "key", key), (rs, rowNumber) -> {
            var cancelledAt = rs.getObject("cancelled_at", OffsetDateTime.class);
            return new StoredReservation(rs.getObject("id", UUID.class),
                    rs.getObject("resource_id", UUID.class), rs.getObject("user_id", UUID.class),
                    rs.getString("idempotency_key"), ReservationStatus.valueOf(rs.getString("status")),
                    rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                    cancelledAt == null ? null : cancelledAt.toInstant());
        });
        return Optional.ofNullable(DataAccessUtils.singleResult(rows));
    }
}
