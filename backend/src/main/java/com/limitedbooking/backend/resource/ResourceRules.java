package com.limitedbooking.backend.resource;

public final class ResourceRules {
    private ResourceRules() {}

    public static void validateCapacity(int capacity) {
        if (capacity < 0 || capacity > 1000) {
            throw new IllegalArgumentException("Capacity must be between 0 and 1000");
        }
    }

    public static String normalizeName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Name is required");
        }
        String normalized = name.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > 100) {
            throw new IllegalArgumentException("Name must contain 1 to 100 code points");
        }
        return normalized;
    }
}
