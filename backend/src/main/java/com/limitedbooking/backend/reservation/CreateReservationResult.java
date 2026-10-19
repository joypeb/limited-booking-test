package com.limitedbooking.backend.reservation;

public record CreateReservationResult(CreatedReservationView body, boolean replayed) {}
