package com.railpredictor.model.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class RemainingRouteHistoricalSummaryTest {

    @Test
    void zeroAvailableSectionsMustCarryNoTotal() {
        assertDoesNotThrow(() -> new RemainingRouteHistoricalSummary(
                "12952", RouteCompleteness.COMPLETE, List.of(),
                RemainingRouteHistoricalStatus.NO_REMAINING_SECTIONS, 0, null, DataProvenance.UNAVAILABLE));
    }

    @Test
    void zeroAvailableSectionsWithATotalIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RemainingRouteHistoricalSummary(
                "12952", RouteCompleteness.COMPLETE, List.of(),
                RemainingRouteHistoricalStatus.NO_SECTIONS_AVAILABLE, 0, 5.0, DataProvenance.UNAVAILABLE));
    }

    @Test
    void positiveAvailableSectionsRequireATotal() {
        assertThrows(IllegalArgumentException.class, () -> new RemainingRouteHistoricalSummary(
                "12952", RouteCompleteness.COMPLETE, List.of(),
                RemainingRouteHistoricalStatus.ALL_SECTIONS_AVAILABLE, 1, null, DataProvenance.RAILRADAR));
    }
}
