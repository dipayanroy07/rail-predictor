package com.railpredictor.model.domain;

/**
 * Shared invariant checks for domain record compact constructors. Package-private: it is an
 * implementation detail of this package, not part of the domain's public API.
 */
final class Guard {

    private Guard() {
    }

    static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }

    static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    static int requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative: " + value);
        }
        return value;
    }

    static double requireNonNegative(double value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative: " + value);
        }
        return value;
    }

    static double requireInRange(double value, double min, double max, String field) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max + ": " + value);
        }
        return value;
    }
}
