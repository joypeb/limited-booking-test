package com.limitedbooking.backend.reservation;

import java.time.Instant;
import java.util.UUID;

public record CreatedReservationView(UUID id, UUID resourceId, ReservationStatus status, Instant createdAt) {
    static CreatedReservationView from(StoredReservation stored) {
        return new CreatedReservationView(stored.id(), stored.resourceId(), ReservationStatus.CONFIRMED, stored.createdAt());
    }
}
