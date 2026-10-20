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
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationView cancel(UUID userId, UUID reservationId) {
        jdbc.execute("SET LOCAL lock_timeout = '2s'");
        jdbc.execute("SET LOCAL statement_timeout = '5s'");
        var stored = reservations.findByIdAndUser(reservationId, userId)
                .orElseThrow(ReservationService.ReservationNotFoundException::new);
        if (CancellationDecision.from(stored.status()) == CancellationDecision.ALREADY_CANCELLED) {
            return ReservationView.from(stored);
        }
        // 생성과 동일하게 resource → reservation 순서로 갱신 잠금을 얻는다.
        resources.lockForUpdate(stored.resourceId());
        int changed = reservations.markCancelled(reservationId, userId,
                Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
        if (changed == 1 && resources.incrementIfBelowCapacity(stored.resourceId()) != 1) {
            throw new IllegalStateException("취소 수량 복구 불변식 위반");
        }
        if (changed != 0 && changed != 1) throw new IllegalStateException("취소 상태 갱신 건수 오류");
        return ReservationView.from(reservations.findByIdAndUser(reservationId, userId)
                .orElseThrow(ReservationService.ReservationNotFoundException::new));
    }

}
