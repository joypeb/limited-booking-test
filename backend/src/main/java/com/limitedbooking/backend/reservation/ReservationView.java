package com.limitedbooking.backend.reservation;

import java.time.Instant;
import java.util.UUID;

/** 생성 응답과 구분되는 현재 예약 상태. cancelledAt은 null도 응답에 포함한다. */
public record ReservationView(UUID id, UUID resourceId, ReservationStatus status,
                              Instant createdAt, Instant cancelledAt) {
    public static ReservationView from(StoredReservation stored) {
        return new ReservationView(stored.id(), stored.resourceId(), stored.status(),
                stored.createdAt(), stored.cancelledAt());
    }
}
