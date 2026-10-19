package com.limitedbooking.backend.reservation;

import com.limitedbooking.backend.resource.ResourceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
public class ReservationWriter {
    private final ResourceRepository resources;
    private final ReservationRepository reservations;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ReservationWriter(ResourceRepository resources, ReservationRepository reservations, JdbcTemplate jdbc, Clock clock) {
        this.resources = resources;
        this.reservations = reservations;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public static final class SoldOutException extends RuntimeException {}

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CreateReservationResult create(UUID userId, UUID resourceId, IdempotencyKey key) {
        jdbc.execute("SET LOCAL lock_timeout = '2s'");
        jdbc.execute("SET LOCAL statement_timeout = '5s'");
        int changed = resources.decrementIfAvailable(resourceId);
        if (changed == 0) {
            var existing = reservations.findByUserAndKey(userId, key.value());
            if (existing.isPresent()) return ReservationService.replay(existing.get(), resourceId);
            if (resources.find(resourceId).isEmpty()) {
                throw new com.limitedbooking.backend.resource.ResourceService.ResourceNotFoundException(resourceId);
            }
            throw new SoldOutException();
        }
        if (changed != 1) throw new IllegalStateException("예약 수량 차감 실패");
        var stored = reservations.insert(UUID.randomUUID(), resourceId, userId, key,
                Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
        return new CreateReservationResult(CreatedReservationView.from(stored), false);
    }
}
