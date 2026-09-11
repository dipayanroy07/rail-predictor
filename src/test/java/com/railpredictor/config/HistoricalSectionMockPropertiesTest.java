package com.railpredictor.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class HistoricalSectionMockPropertiesTest {

    @Test
    void allowsANegativeAverageOrMedianDelayChange() {
        // Unlike HistoricalMockProperties, a negative delay change (recovery) is a legitimate value.
        assertDoesNotThrow(() -> new HistoricalSectionMockProperties(-5.0, -3.0, 0, 0));
    }

    @Test
    void rejectsANegativeStandardDeviation() {
        assertThrows(IllegalArgumentException.class, () -> new HistoricalSectionMockProperties(0, 0, -1, 0));
    }

    @Test
    void rejectsANegativeSampleCount() {
        assertThrows(IllegalArgumentException.class, () -> new HistoricalSectionMockProperties(0, 0, 0, -1));
    }
}
