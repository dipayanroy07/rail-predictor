package com.railpredictor.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class HistoricalSectionPropertiesTest {

    @Test
    void allowsZeroAndPositiveThresholds() {
        assertDoesNotThrow(() -> new HistoricalSectionProperties(0));
        assertDoesNotThrow(() -> new HistoricalSectionProperties(10));
    }

    @Test
    void rejectsANegativeThreshold() {
        assertThrows(IllegalArgumentException.class, () -> new HistoricalSectionProperties(-1));
    }
}
