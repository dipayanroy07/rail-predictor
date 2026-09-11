package com.railpredictor.model.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RailwayDisruptionQueryResultTest {

    private static RailwayDisruption disruption() {
        return new RailwayDisruption(
                RailwayDisruptionType.TEMPORARY_SPEED_RESTRICTION, "12952", "KOTA", "RTM",
                40.0, null, Instant.parse("2026-09-10T00:00:00Z"), null,
                Instant.parse("2026-09-10T06:00:00Z"), DataProvenance.MOCK, null);
    }

    @Test
    void unavailableWithAnEmptyListIsValid() {
        assertDoesNotThrow(() -> new RailwayDisruptionQueryResult(
                "12952", "KOTA", "RTM", RailwayDisruptionAvailability.UNAVAILABLE, List.of()));
    }

    @Test
    void unavailableWithANonEmptyListIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RailwayDisruptionQueryResult(
                "12952", "KOTA", "RTM", RailwayDisruptionAvailability.UNAVAILABLE, List.of(disruption())));
    }

    @Test
    void availableWithAnEmptyListMeansNoKnownDisruption() {
        RailwayDisruptionQueryResult result = new RailwayDisruptionQueryResult(
                "12952", "KOTA", "RTM", RailwayDisruptionAvailability.AVAILABLE, List.of());

        assertThat(result.disruptions()).isEmpty();
        assertThat(result.availability()).isEqualTo(RailwayDisruptionAvailability.AVAILABLE);
    }

    @Test
    void availableWithOneDisruptionIsValid() {
        RailwayDisruptionQueryResult result = new RailwayDisruptionQueryResult(
                "12952", "KOTA", "RTM", RailwayDisruptionAvailability.AVAILABLE, List.of(disruption()));

        assertThat(result.disruptions()).hasSize(1);
    }

    @Test
    void availableWithMultipleDisruptionsIsValid() {
        RailwayDisruption other = new RailwayDisruption(
                RailwayDisruptionType.SIGNAL_FAILURE, null, "KOTA", "RTM",
                null, null, null, null, Instant.parse("2026-09-10T06:00:00Z"), DataProvenance.MOCK, null);

        RailwayDisruptionQueryResult result = new RailwayDisruptionQueryResult(
                "12952", "KOTA", "RTM", RailwayDisruptionAvailability.AVAILABLE, List.of(disruption(), other));

        assertThat(result.disruptions()).hasSize(2);
    }

    @Test
    void rejectsABlankTrainNumber() {
        assertThrows(IllegalArgumentException.class, () -> new RailwayDisruptionQueryResult(
                " ", "KOTA", "RTM", RailwayDisruptionAvailability.AVAILABLE, List.of()));
    }

    @Test
    void rejectsBlankStationCodes() {
        assertThrows(IllegalArgumentException.class, () -> new RailwayDisruptionQueryResult(
                "12952", " ", "RTM", RailwayDisruptionAvailability.AVAILABLE, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RailwayDisruptionQueryResult(
                "12952", "KOTA", " ", RailwayDisruptionAvailability.AVAILABLE, List.of()));
    }
}
