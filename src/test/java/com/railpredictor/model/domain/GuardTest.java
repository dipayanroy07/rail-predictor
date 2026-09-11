package com.railpredictor.model.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GuardTest {

    @Test
    void requireNonNullReturnsValueWhenPresent() {
        assertEquals("x", Guard.requireNonNull("x", "field"));
    }

    @Test
    void requireNonNullRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> Guard.requireNonNull(null, "field"));
    }

    @Test
    void requireNonBlankRejectsNullAndBlank() {
        assertThrows(IllegalArgumentException.class, () -> Guard.requireNonBlank(null, "field"));
        assertThrows(IllegalArgumentException.class, () -> Guard.requireNonBlank("   ", "field"));
    }

    @Test
    void requireNonNegativeAcceptsZero() {
        assertEquals(0, Guard.requireNonNegative(0, "field"));
        assertEquals(0.0, Guard.requireNonNegative(0.0, "field"));
    }

    @Test
    void requireNonNegativeRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> Guard.requireNonNegative(-1, "field"));
        assertThrows(IllegalArgumentException.class, () -> Guard.requireNonNegative(-0.1, "field"));
    }

    @Test
    void requireInRangeAcceptsBoundsInclusive() {
        assertEquals(0.0, Guard.requireInRange(0.0, 0, 100, "field"));
        assertEquals(100.0, Guard.requireInRange(100.0, 0, 100, "field"));
    }

    @Test
    void requireInRangeRejectsOutsideBounds() {
        assertThrows(IllegalArgumentException.class, () -> Guard.requireInRange(-0.1, 0, 100, "field"));
        assertThrows(IllegalArgumentException.class, () -> Guard.requireInRange(100.1, 0, 100, "field"));
    }
}
