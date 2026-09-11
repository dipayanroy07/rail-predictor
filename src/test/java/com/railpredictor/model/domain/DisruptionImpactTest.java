package com.railpredictor.model.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DisruptionImpactTest {

    @Test
    void estimatedWithNonNegativeMinutesIsValid() {
        DisruptionImpact impact = new DisruptionImpact(
                RailwayDisruptionType.TEMPORARY_SPEED_RESTRICTION, DisruptionImpactStatus.ESTIMATED,
                12, "computed", DataProvenance.MOCK);

        assertThat(impact.additionalDelayMinutes()).isEqualTo(12);
    }

    @Test
    void estimatedWithZeroMinutesIsValid() {
        assertDoesNotThrow(() -> new DisruptionImpact(
                RailwayDisruptionType.CONGESTION, DisruptionImpactStatus.ESTIMATED, 0, "zero", DataProvenance.MOCK));
    }

    @Test
    void estimatedWithoutMinutesIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DisruptionImpact(
                RailwayDisruptionType.CONGESTION, DisruptionImpactStatus.ESTIMATED, null, "missing", DataProvenance.MOCK));
    }

    @Test
    void estimatedWithNegativeMinutesIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DisruptionImpact(
                RailwayDisruptionType.CONGESTION, DisruptionImpactStatus.ESTIMATED, -1, "negative", DataProvenance.MOCK));
    }

    @Test
    void notEstimableWithMinutesIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DisruptionImpact(
                RailwayDisruptionType.OTHER, DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE, 5, "should be null", DataProvenance.MOCK));
    }

    @Test
    void notEstimableWithoutMinutesIsValid() {
        assertDoesNotThrow(() -> new DisruptionImpact(
                RailwayDisruptionType.OTHER, DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE, null, "unknown", DataProvenance.MOCK));
    }

    @Test
    void rejectsNoKnownDisruptionAndDataUnavailableAsPerDisruptionStatuses() {
        assertThrows(IllegalArgumentException.class, () -> new DisruptionImpact(
                RailwayDisruptionType.OTHER, DisruptionImpactStatus.NO_KNOWN_DISRUPTION, 0, "wrong level", DataProvenance.MOCK));
        assertThrows(IllegalArgumentException.class, () -> new DisruptionImpact(
                RailwayDisruptionType.OTHER, DisruptionImpactStatus.DATA_UNAVAILABLE, null, "wrong level", DataProvenance.MOCK));
    }

    @Test
    void rejectsANullType() {
        assertThrows(IllegalArgumentException.class, () -> new DisruptionImpact(
                null, DisruptionImpactStatus.ESTIMATED, 5, "r", DataProvenance.MOCK));
    }

    @Test
    void rejectsABlankRationale() {
        assertThrows(IllegalArgumentException.class, () -> new DisruptionImpact(
                RailwayDisruptionType.CONGESTION, DisruptionImpactStatus.ESTIMATED, 5, " ", DataProvenance.MOCK));
    }

    @Test
    void rejectsABlankSourceProvenance() {
        assertThrows(IllegalArgumentException.class, () -> new DisruptionImpact(
                RailwayDisruptionType.CONGESTION, DisruptionImpactStatus.ESTIMATED, 5, "r", " "));
    }
}
