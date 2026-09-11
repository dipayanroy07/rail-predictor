package com.railpredictor.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SpeedRestrictionConfigTest {

    @Test
    void acceptsZero() {
        assertDoesNotThrow(() -> new SpeedRestrictionConfig(0));
    }

    @Test
    void rejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new SpeedRestrictionConfig(-1));
    }
}
