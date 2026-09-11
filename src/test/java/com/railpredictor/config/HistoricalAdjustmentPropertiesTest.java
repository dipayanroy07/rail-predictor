package com.railpredictor.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class HistoricalAdjustmentPropertiesTest {

    @Test
    void acceptsZeroWeightAndZeroMinimumSampleCount() {
        assertDoesNotThrow(() -> new HistoricalAdjustmentProperties(0.0, 0));
    }

    @Test
    void rejectsNegativeWeightOrMinimumSampleCount() {
        assertThrows(IllegalArgumentException.class, () -> new HistoricalAdjustmentProperties(-0.1, 5));
        assertThrows(IllegalArgumentException.class, () -> new HistoricalAdjustmentProperties(0.2, -1));
    }
}
