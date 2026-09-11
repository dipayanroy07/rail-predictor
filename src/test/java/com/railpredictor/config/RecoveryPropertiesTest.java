package com.railpredictor.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RecoveryPropertiesTest {

    @Test
    void acceptsBoundaryValues() {
        assertDoesNotThrow(() -> new RecoveryProperties(0.0, 0.0, 0.0, 0));
        assertDoesNotThrow(() -> new RecoveryProperties(1.0, 1.0, 100.0, 100));
    }

    @Test
    void rejectsOutOfRangeFractionOrMultiplier() {
        assertThrows(IllegalArgumentException.class, () -> new RecoveryProperties(-0.1, 0.5, 1.0, 10));
        assertThrows(IllegalArgumentException.class, () -> new RecoveryProperties(1.1, 0.5, 1.0, 10));
        assertThrows(IllegalArgumentException.class, () -> new RecoveryProperties(0.2, -0.1, 1.0, 10));
        assertThrows(IllegalArgumentException.class, () -> new RecoveryProperties(0.2, 1.1, 1.0, 10));
    }

    @Test
    void rejectsNegativeCaps() {
        assertThrows(IllegalArgumentException.class, () -> new RecoveryProperties(0.2, 0.5, -1.0, 10));
        assertThrows(IllegalArgumentException.class, () -> new RecoveryProperties(0.2, 0.5, 1.0, -1));
    }
}
