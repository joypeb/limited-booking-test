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
    public static final class ReservationNotFoundException extends RuntimeException {}
    public static final class IdempotencyConflictException extends RuntimeException {}
    public CreateReservationResult create(UUID userId, UUID resourceId, IdempotencyKey key) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("예약 생성 Service에는 상위 트랜잭션을 사용할 수 없습니다.");
        }
        var existing = reservations.findByUserAndKey(userId, key.value());
        if (existing.isPresent()) return replay(existing.get(), resourceId);
        try {
            return writer.create(userId, resourceId, key);
        } catch (RuntimeException failure) {
            if (!com.limitedbooking.backend.common.UniqueViolationClassifier.isReservationUserKeyViolation(failure)) throw failure;
            // Writer 프록시의 전체 롤백이 끝난 뒤 트랜잭션 밖의 새 조회다.
            var committed = reservations.findByUserAndKey(userId, key.value())
                    .orElseThrow(() -> new IllegalStateException("중복 생성 키의 성공 기록이 없습니다.", failure));
            return replay(committed, resourceId);
        }
    }
    public ReservationView get(UUID userId, UUID reservationId) {
        return ReservationView.from(reservations.findByIdAndUser(reservationId, userId)
                .orElseThrow(ReservationNotFoundException::new));
    }

    public ReservationView cancel(UUID userId, UUID reservationId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("예약 취소 Service에는 상위 트랜잭션을 사용할 수 없습니다.");
        }
        return writer.cancel(userId, reservationId);
    }

    static CreateReservationResult replay(StoredReservation stored, UUID resourceId) {
        if (!stored.resourceId().equals(resourceId)) throw new IdempotencyConflictException();
        return new CreateReservationResult(CreatedReservationView.from(stored), true);
    }
}
