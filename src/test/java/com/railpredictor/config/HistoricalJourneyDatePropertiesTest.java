package com.railpredictor.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class HistoricalJourneyDatePropertiesTest {

    @Test
    void allowsEveryHourOfTheDay() {
        assertDoesNotThrow(() -> new HistoricalJourneyDateProperties(0));
        assertDoesNotThrow(() -> new HistoricalJourneyDateProperties(23));
        assertDoesNotThrow(() -> new HistoricalJourneyDateProperties(3));
    }

    @Test
    void rejectsAnHourOutsideZeroToTwentyThree() {
        assertThrows(IllegalArgumentException.class, () -> new HistoricalJourneyDateProperties(-1));
        assertThrows(IllegalArgumentException.class, () -> new HistoricalJourneyDateProperties(24));
    }
}
