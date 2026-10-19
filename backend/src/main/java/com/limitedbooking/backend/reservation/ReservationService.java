package com.limitedbooking.backend.reservation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.UUID;

@Service
public class ReservationService {
    private final ReservationWriter writer;
    public ReservationService(ReservationWriter writer) { this.writer = writer; }
    public CreateReservationResult create(UUID userId, UUID resourceId, IdempotencyKey key) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("예약 생성 Service에는 상위 트랜잭션을 사용할 수 없습니다.");
        }
        return writer.create(userId, resourceId, key);
    }
}
