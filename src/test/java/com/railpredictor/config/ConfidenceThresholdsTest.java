package com.railpredictor.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ConfidenceThresholdsTest {

    @Test
    void acceptsEqualThresholds() {
        assertDoesNotThrow(() -> new ConfidenceThresholds(50, 50, 50));
    }

    @Test
    void rejectsOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class, () -> new ConfidenceThresholds(-1, 60, 35));
        assertThrows(IllegalArgumentException.class, () -> new ConfidenceThresholds(101, 60, 35));
    }

    @Test
    void rejectsOutOfOrderThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new ConfidenceThresholds(60, 80, 35));
        assertThrows(IllegalArgumentException.class, () -> new ConfidenceThresholds(80, 35, 60));
    }
}
