package com.railpredictor.model.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SectionHistoricalDelayResultTest {

    private static HistoricalSectionDelayProfile profile() {
        return new HistoricalSectionDelayProfile(
                "12952", "A", "B", 5, 3.0, 3.0, 1.0, DataProvenance.RAILRADAR, Instant.parse("2026-09-09T09:00:00Z"));
    }

    @Test
    void notFoundMustCarryNoProfile() {
        assertDoesNotThrow(() -> new SectionHistoricalDelayResult(
                "12952", "A", "B", SectionHistoricalDelayStatus.NOT_FOUND, null));
    }

    @Test
    void notFoundWithAProfileAttachedIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SectionHistoricalDelayResult(
                "12952", "A", "B", SectionHistoricalDelayStatus.NOT_FOUND, profile()));
    }

    @Test
    void availableRequiresAProfile() {
        assertThrows(IllegalArgumentException.class, () -> new SectionHistoricalDelayResult(
                "12952", "A", "B", SectionHistoricalDelayStatus.AVAILABLE, null));
    }

    @Test
    void insufficientSamplesRequiresAProfile() {
        assertThrows(IllegalArgumentException.class, () -> new SectionHistoricalDelayResult(
                "12952", "A", "B", SectionHistoricalDelayStatus.INSUFFICIENT_SAMPLES, null));
    }

    @Test
    void availableWithAProfileIsValid() {
        assertDoesNotThrow(() -> new SectionHistoricalDelayResult(
                "12952", "A", "B", SectionHistoricalDelayStatus.AVAILABLE, profile()));
    }
}
