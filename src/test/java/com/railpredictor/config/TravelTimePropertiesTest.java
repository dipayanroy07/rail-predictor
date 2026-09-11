package com.railpredictor.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TravelTimePropertiesTest {

    @Test
    void acceptsPositiveSpeed() {
        assertDoesNotThrow(() -> new TravelTimeProperties(60.0));
    }

    @Test
    void rejectsZeroOrNegativeSpeed() {
        assertThrows(IllegalArgumentException.class, () -> new TravelTimeProperties(0.0));
        assertThrows(IllegalArgumentException.class, () -> new TravelTimeProperties(-1.0));
    }
}
