package com.limitedbooking.backend.reservation;

import java.util.regex.Pattern;

public record IdempotencyKey(String value) {
    private static final Pattern VALID_KEY = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    public IdempotencyKey {
        if (value == null || !VALID_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid idempotency key");
        }
    }

    public static IdempotencyKey parse(String value) {
        return new IdempotencyKey(value);
    }
}
