package com.railpredictor.model.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class DisruptionImpactAssessmentTest {

    private static DisruptionImpact estimated(int minutes) {
        return new DisruptionImpact(
                RailwayDisruptionType.CONGESTION, DisruptionImpactStatus.ESTIMATED, minutes, "r", DataProvenance.MOCK);
    }

    private static DisruptionImpact notEstimable() {
        return new DisruptionImpact(
                RailwayDisruptionType.OTHER, DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE, null, "r", DataProvenance.MOCK);
    }

    @Test
    void unavailableFactoryProducesTheExplicitUnavailableState() {
        DisruptionImpactAssessment assessment = DisruptionImpactAssessment.unavailable();

        assertThat(assessment.status()).isEqualTo(DisruptionImpactStatus.DATA_UNAVAILABLE);
        assertThat(assessment.additionalDelayMinutes()).isNull();
        assertThat(assessment.contributingImpacts()).isEmpty();
        assertThat(assessment.calibrationStatus()).isEqualTo(CalibrationStatus.INSUFFICIENT_DATA);
    }

    @Test
    void noKnownDisruptionRequiresZeroMinutesNotNull() {
        assertThrows(IllegalArgumentException.class, () -> new DisruptionImpactAssessment(
                DisruptionImpactStatus.NO_KNOWN_DISRUPTION, null, List.of(), false, CalibrationStatus.INSUFFICIENT_DATA));
        assertDoesNotThrow(() -> new DisruptionImpactAssessment(
                DisruptionImpactStatus.NO_KNOWN_DISRUPTION, 0, List.of(), false, CalibrationStatus.INSUFFICIENT_DATA));
    }

    @Test
    void dataUnavailableRequiresNullMinutes() {
        assertThrows(IllegalArgumentException.class, () -> new DisruptionImpactAssessment(
                DisruptionImpactStatus.DATA_UNAVAILABLE, 0, List.of(), false, CalibrationStatus.INSUFFICIENT_DATA));
    }

    @Test
    void dataUnavailableRequiresEmptyContributingImpacts() {
        assertThrows(IllegalArgumentException.class, () -> new DisruptionImpactAssessment(
                DisruptionImpactStatus.DATA_UNAVAILABLE, null, List.of(notEstimable()), false, CalibrationStatus.INSUFFICIENT_DATA));
    }

    @Test
    void noKnownDisruptionRequiresEmptyContributingImpacts() {
        assertThrows(IllegalArgumentException.class, () -> new DisruptionImpactAssessment(
                DisruptionImpactStatus.NO_KNOWN_DISRUPTION, 0, List.of(estimated(5)), false, CalibrationStatus.INSUFFICIENT_DATA));
    }

    @Test
    void presentButNotEstimableRequiresNullMinutesButAllowsContributingImpacts() {
        assertDoesNotThrow(() -> new DisruptionImpactAssessment(
                DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE, null, List.of(notEstimable()), false, CalibrationStatus.INSUFFICIENT_DATA));
        assertThrows(IllegalArgumentException.class, () -> new DisruptionImpactAssessment(
                DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE, 0, List.of(notEstimable()), false, CalibrationStatus.INSUFFICIENT_DATA));
    }

    @Test
    void estimatedRequiresNonNegativeMinutesAndAllowsContributingImpacts() {
        assertDoesNotThrow(() -> new DisruptionImpactAssessment(
                DisruptionImpactStatus.ESTIMATED, 25, List.of(estimated(15), estimated(10)), false, CalibrationStatus.INSUFFICIENT_DATA));
        assertThrows(IllegalArgumentException.class, () -> new DisruptionImpactAssessment(
                DisruptionImpactStatus.ESTIMATED, null, List.of(estimated(15)), false, CalibrationStatus.INSUFFICIENT_DATA));
    }
}
