package com.railpredictor.model.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class HistoricalDelayProfileTest {

    private static HistoricalDelayProfile valid(double average, double median) {
        return new HistoricalDelayProfile(
                "12952", "KOTA", 10, average, median, 3.5, DataProvenance.RAILRADAR, Instant.now());
    }

    @Test
    void constructsSuccessfullyWithValidFields() {
        assertDoesNotThrow(() -> valid(8.0, 6.0));
    }

    @Test
    void allowsNegativeAverageAndMedianBecauseTheyAreUnclampedRawStatistics() {
        // Unlike HistoricalDelay, this profile must report a train historically running early.
        assertDoesNotThrow(() -> valid(-2.0, -1.0));
    }

    @Test
    void rejectsNegativeSampleCount() {
        assertThrows(IllegalArgumentException.class, () -> new HistoricalDelayProfile(
                "12952", "KOTA", -1, 8.0, 6.0, 3.5, DataProvenance.RAILRADAR, Instant.now()));
    }

    @Test
    void rejectsNegativeStandardDeviation() {
        assertThrows(IllegalArgumentException.class, () -> new HistoricalDelayProfile(
                "12952", "KOTA", 10, 8.0, 6.0, -0.1, DataProvenance.RAILRADAR, Instant.now()));
    }

    @Test
    void rejectsBlankOrNullIdentifyingFields() {
        assertThrows(IllegalArgumentException.class, () -> new HistoricalDelayProfile(
                " ", "KOTA", 10, 8.0, 6.0, 3.5, DataProvenance.RAILRADAR, Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new HistoricalDelayProfile(
                "12952", " ", 10, 8.0, 6.0, 3.5, DataProvenance.RAILRADAR, Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new HistoricalDelayProfile(
                "12952", "KOTA", 10, 8.0, 6.0, 3.5, " ", Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new HistoricalDelayProfile(
                "12952", "KOTA", 10, 8.0, 6.0, 3.5, DataProvenance.RAILRADAR, null));
    }

    @Test
    void allowsZeroSampleCountToRepresentNoData() {
        assertDoesNotThrow(() -> new HistoricalDelayProfile(
                "12952", "KOTA", 0, 0.0, 0.0, 0.0, DataProvenance.UNAVAILABLE, Instant.now()));
    }
}
