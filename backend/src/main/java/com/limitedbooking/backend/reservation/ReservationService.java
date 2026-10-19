package com.limitedbooking.backend.reservation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.UUID;

@Service
public class ReservationService {
    private final ReservationWriter writer;
    private final ReservationRepository reservations;
    public ReservationService(ReservationWriter writer, ReservationRepository reservations) {
        this.writer = writer; this.reservations = reservations;
    }
    public static final class IdempotencyConflictException extends RuntimeException {}
    public CreateReservationResult create(UUID userId, UUID resourceId, IdempotencyKey key) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("예약 생성 Service에는 상위 트랜잭션을 사용할 수 없습니다.");
        }
        var existing = reservations.findByUserAndKey(userId, key.value());
        if (existing.isPresent()) return replay(existing.get(), resourceId);
        return writer.create(userId, resourceId, key);
    }
    static CreateReservationResult replay(StoredReservation stored, UUID resourceId) {
        if (!stored.resourceId().equals(resourceId)) throw new IdempotencyConflictException();
        return new CreateReservationResult(CreatedReservationView.from(stored), true);
    }
}
