package com.railpredictor.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class HistoricalMockPropertiesTest {

    @Test
    void allowsAllZerosToRepresentNoData() {
        assertDoesNotThrow(() -> new HistoricalMockProperties(0, 0, 0, 0, null));
    }

    @Test
    void rejectsNegativeStatistics() {
        assertThrows(IllegalArgumentException.class, () -> new HistoricalMockProperties(-1, 0, 0, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new HistoricalMockProperties(0, -1, 0, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new HistoricalMockProperties(0, 0, -1, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new HistoricalMockProperties(0, 0, 0, -1, null));
    }
}
