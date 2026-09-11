package com.railpredictor.model.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.DayOfWeek;
import java.time.Month;
import org.junit.jupiter.api.Test;

class HistoricalDelayTest {

    @Test
    void allowsZeroSampleCountToRepresentNoData() {
        assertDoesNotThrow(() -> new HistoricalDelay(
                "12345", DomainFixtures.section(), DayOfWeek.MONDAY, Month.JANUARY, null,
                0.0, 0.0, 0.0, 0, DataProvenance.MOCK));
    }

    @Test
    void rejectsNegativeStatistics() {
        assertThrows(IllegalArgumentException.class, () -> new HistoricalDelay(
                "12345", DomainFixtures.section(), DayOfWeek.MONDAY, Month.JANUARY, null,
                -1.0, 0.0, 0.0, 10, DataProvenance.MOCK));
        assertThrows(IllegalArgumentException.class, () -> new HistoricalDelay(
                "12345", DomainFixtures.section(), DayOfWeek.MONDAY, Month.JANUARY, null,
                0.0, 0.0, 0.0, -1, DataProvenance.MOCK));
    }

    @Test
    void rejectsMissingIdentifyingFields() {
        assertThrows(IllegalArgumentException.class, () -> new HistoricalDelay(
                " ", DomainFixtures.section(), DayOfWeek.MONDAY, Month.JANUARY, null,
                0.0, 0.0, 0.0, 0, DataProvenance.MOCK));
        assertThrows(IllegalArgumentException.class, () -> new HistoricalDelay(
                "12345", null, DayOfWeek.MONDAY, Month.JANUARY, null,
                0.0, 0.0, 0.0, 0, DataProvenance.MOCK));
    }

    @Test
    void rejectsBlankOrNullSource() {
        assertThrows(IllegalArgumentException.class, () -> new HistoricalDelay(
                "12345", DomainFixtures.section(), DayOfWeek.MONDAY, Month.JANUARY, null,
                0.0, 0.0, 0.0, 0, " "));
        assertThrows(IllegalArgumentException.class, () -> new HistoricalDelay(
                "12345", DomainFixtures.section(), DayOfWeek.MONDAY, Month.JANUARY, null,
                0.0, 0.0, 0.0, 0, null));
    }
}
