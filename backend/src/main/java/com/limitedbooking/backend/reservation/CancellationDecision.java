package com.limitedbooking.backend.reservation;

import java.util.Objects;

public enum CancellationDecision {
    CANCEL_NOW,
    ALREADY_CANCELLED;

    public static CancellationDecision from(ReservationStatus status) {
        Objects.requireNonNull(status, "Reservation status is required");
        return switch (status) {
            case CONFIRMED -> CANCEL_NOW;
            case CANCELLED -> ALREADY_CANCELLED;
        };
    }
}
