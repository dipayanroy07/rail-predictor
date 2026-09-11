package com.railpredictor.model.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class HistoricalAdjustmentResolutionTest {

    @Test
    void sourceAndProvenanceVaryIndependently() {
        // A SECTION-sourced adjustment can be mock-provenanced exactly as easily as a
        // STATION_FALLBACK one - the two axes are genuinely independent, never conflated.
        assertDoesNotThrow(() -> new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.SECTION, DataProvenance.MOCK));
        assertDoesNotThrow(() -> new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.MOCK));
        assertDoesNotThrow(() -> new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.SECTION, DataProvenance.RAILRADAR));
        assertDoesNotThrow(() -> new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.NONE, DataProvenance.UNAVAILABLE));
    }

    @Test
    void rejectsANullSource() {
        assertThrows(IllegalArgumentException.class, () -> new HistoricalAdjustmentResolution(null, DataProvenance.RAILRADAR));
    }

    @Test
    void rejectsABlankProvenance() {
        assertThrows(IllegalArgumentException.class, () -> new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.NONE, " "));
    }
}
