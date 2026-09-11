package com.railpredictor.model.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RailwayDisruptionTest {

    private static RailwayDisruption valid() {
        return new RailwayDisruption(
                RailwayDisruptionType.TEMPORARY_SPEED_RESTRICTION, "12952", "KOTA", "RTM",
                40.0, "moderate", Instant.parse("2026-09-10T00:00:00Z"), null,
                Instant.parse("2026-09-10T06:00:00Z"), DataProvenance.MOCK, "TSR-1234");
    }

    @Test
    void validDisruptionConstructsSuccessfully() {
        assertDoesNotThrow(RailwayDisruptionTest::valid);
    }

    @Test
    void allowsARouteWideDisruptionWithNoTrainNumber() {
        RailwayDisruption disruption = new RailwayDisruption(
                RailwayDisruptionType.ENGINEERING_BLOCK, null, "KOTA", "RTM",
                null, null, null, null, Instant.parse("2026-09-10T06:00:00Z"), DataProvenance.MOCK, null);

        assertThat(disruption.trainNumber()).isNull();
    }

    @Test
    void rejectsANullType() {
        RailwayDisruption v = valid();
        assertThrows(IllegalArgumentException.class, () -> new RailwayDisruption(
                null, v.trainNumber(), v.fromStationCode(), v.toStationCode(), v.restrictedSpeedKmh(),
                v.severity(), v.effectiveFrom(), v.effectiveUntil(), v.observedAt(), v.source(), v.sourceReference()));
    }

    @Test
    void rejectsABlankFromStationCode() {
        RailwayDisruption v = valid();
        assertThrows(IllegalArgumentException.class, () -> new RailwayDisruption(
                v.type(), v.trainNumber(), " ", v.toStationCode(), v.restrictedSpeedKmh(),
                v.severity(), v.effectiveFrom(), v.effectiveUntil(), v.observedAt(), v.source(), v.sourceReference()));
    }

    @Test
    void rejectsABlankToStationCode() {
        RailwayDisruption v = valid();
        assertThrows(IllegalArgumentException.class, () -> new RailwayDisruption(
                v.type(), v.trainNumber(), v.fromStationCode(), " ", v.restrictedSpeedKmh(),
                v.severity(), v.effectiveFrom(), v.effectiveUntil(), v.observedAt(), v.source(), v.sourceReference()));
    }

    @Test
    void rejectsANullObservedAt() {
        RailwayDisruption v = valid();
        assertThrows(IllegalArgumentException.class, () -> new RailwayDisruption(
                v.type(), v.trainNumber(), v.fromStationCode(), v.toStationCode(), v.restrictedSpeedKmh(),
                v.severity(), v.effectiveFrom(), v.effectiveUntil(), null, v.source(), v.sourceReference()));
    }

    @Test
    void rejectsABlankSource() {
        RailwayDisruption v = valid();
        assertThrows(IllegalArgumentException.class, () -> new RailwayDisruption(
                v.type(), v.trainNumber(), v.fromStationCode(), v.toStationCode(), v.restrictedSpeedKmh(),
                v.severity(), v.effectiveFrom(), v.effectiveUntil(), v.observedAt(), " ", v.sourceReference()));
    }

    @Test
    void rejectsANegativeRestrictedSpeed() {
        RailwayDisruption v = valid();
        assertThrows(IllegalArgumentException.class, () -> new RailwayDisruption(
                v.type(), v.trainNumber(), v.fromStationCode(), v.toStationCode(), -1.0,
                v.severity(), v.effectiveFrom(), v.effectiveUntil(), v.observedAt(), v.source(), v.sourceReference()));
    }

    @Test
    void rejectsAnInvertedEffectiveWindow() {
        RailwayDisruption v = valid();
        assertThrows(IllegalArgumentException.class, () -> new RailwayDisruption(
                v.type(), v.trainNumber(), v.fromStationCode(), v.toStationCode(), v.restrictedSpeedKmh(),
                v.severity(), Instant.parse("2026-09-10T12:00:00Z"), Instant.parse("2026-09-10T00:00:00Z"),
                v.observedAt(), v.source(), v.sourceReference()));
    }

    @Test
    void allowsAnOpenEndedEffectiveWindow() {
        RailwayDisruption v = valid();
        assertDoesNotThrow(() -> new RailwayDisruption(
                v.type(), v.trainNumber(), v.fromStationCode(), v.toStationCode(), v.restrictedSpeedKmh(),
                v.severity(), Instant.parse("2026-09-10T00:00:00Z"), null,
                v.observedAt(), v.source(), v.sourceReference()));
    }

    @Test
    void allowsAnUndatedDisruptionWithNoEffectiveFromOrUntil() {
        RailwayDisruption v = valid();
        assertDoesNotThrow(() -> new RailwayDisruption(
                v.type(), v.trainNumber(), v.fromStationCode(), v.toStationCode(), v.restrictedSpeedKmh(),
                v.severity(), null, null, v.observedAt(), v.source(), v.sourceReference()));
    }

    @Test
    void allowsNoRestrictedSpeedSeverityOrSourceReference() {
        RailwayDisruption v = valid();
        RailwayDisruption disruption = new RailwayDisruption(
                v.type(), v.trainNumber(), v.fromStationCode(), v.toStationCode(), null,
                null, v.effectiveFrom(), v.effectiveUntil(), v.observedAt(), v.source(), null);

        assertThat(disruption.restrictedSpeedKmh()).isNull();
        assertThat(disruption.severity()).isNull();
        assertThat(disruption.sourceReference()).isNull();
    }
}
