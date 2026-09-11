package com.railpredictor.model.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class HistoricalObservationTest {

    private static HistoricalObservation valid() {
        return new HistoricalObservation(
                "12952", LocalDate.of(2026, 9, 9), "KOTA", 5,
                "20:39", "20:51", "20:41", "20:53", 12, 12,
                Instant.parse("2026-09-09T15:00:00Z"), DataProvenance.RAILRADAR);
    }

    @Test
    void constructsSuccessfullyWithAllFieldsPresent() {
        assertDoesNotThrow(HistoricalObservationTest::valid);
    }

    @Test
    void allowsAllNullableScheduleAndDelayFieldsToBeNull() {
        assertDoesNotThrow(() -> new HistoricalObservation(
                "12952", LocalDate.of(2026, 9, 9), "KOTA", null,
                null, null, null, null, null, null,
                Instant.parse("2026-09-09T15:00:00Z"), DataProvenance.RAILRADAR));
    }

    @Test
    void allowsNegativeDelaysBecauseTheyAreRawUnclampedFacts() {
        // A train running ahead of schedule - unlike LiveTrainData's currentDelayMinutes, this
        // is a raw observation and must not be clamped.
        HistoricalObservation observation = new HistoricalObservation(
                "12952", LocalDate.of(2026, 9, 9), "KOTA", 5,
                "20:39", "20:35", "20:41", "20:37", -4, -4,
                Instant.parse("2026-09-09T15:00:00Z"), DataProvenance.RAILRADAR);

        assertThat(observation.arrivalDelayMinutes()).isEqualTo(-4);
        assertThat(observation.departureDelayMinutes()).isEqualTo(-4);
    }

    @Test
    void rejectsBlankOrNullIdentifyingFields() {
        assertThrows(IllegalArgumentException.class, () -> new HistoricalObservation(
                " ", LocalDate.of(2026, 9, 9), "KOTA", 5, null, null, null, null, null, null,
                Instant.now(), DataProvenance.RAILRADAR));
        assertThrows(IllegalArgumentException.class, () -> new HistoricalObservation(
                "12952", null, "KOTA", 5, null, null, null, null, null, null,
                Instant.now(), DataProvenance.RAILRADAR));
        assertThrows(IllegalArgumentException.class, () -> new HistoricalObservation(
                "12952", LocalDate.of(2026, 9, 9), " ", 5, null, null, null, null, null, null,
                Instant.now(), DataProvenance.RAILRADAR));
        assertThrows(IllegalArgumentException.class, () -> new HistoricalObservation(
                "12952", LocalDate.of(2026, 9, 9), "KOTA", 5, null, null, null, null, null, null,
                null, DataProvenance.RAILRADAR));
        assertThrows(IllegalArgumentException.class, () -> new HistoricalObservation(
                "12952", LocalDate.of(2026, 9, 9), "KOTA", 5, null, null, null, null, null, null,
                Instant.now(), " "));
    }
}
