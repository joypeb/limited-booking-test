package com.limitedbooking.backend.reservation;
import java.time.Instant;
import java.util.UUID;
public record StoredReservation(UUID id, UUID resourceId, UUID userId, String idempotencyKey,
                                ReservationStatus status, Instant createdAt, Instant cancelledAt) {}
