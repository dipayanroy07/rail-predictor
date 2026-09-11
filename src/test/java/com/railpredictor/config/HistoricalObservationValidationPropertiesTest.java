package com.railpredictor.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class HistoricalObservationValidationPropertiesTest {

    @Test
    void allowsAPositiveBound() {
        assertDoesNotThrow(() -> new HistoricalObservationValidationProperties(4320));
    }

    @Test
    void rejectsZeroOrNegativeBounds() {
        assertThrows(IllegalArgumentException.class, () -> new HistoricalObservationValidationProperties(0));
        assertThrows(IllegalArgumentException.class, () -> new HistoricalObservationValidationProperties(-1));
    }
}
