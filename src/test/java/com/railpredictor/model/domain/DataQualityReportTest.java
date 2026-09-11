package com.railpredictor.model.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DataQualityReportTest {

    @Test
    void emptyFactoryHasZeroCountsAndTheGivenNote() {
        DataQualityReport report = DataQualityReport.empty("no data yet");

        assertThat(report.totalSnapshots()).isZero();
        assertThat(report.pointInTimeReproducibilityNote()).isEqualTo("no data yet");
    }

    @Test
    void rejectsNegativeCounts() {
        assertThatThrownBy(() -> new DataQualityReport(
                -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, null, "note"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsExactPlusApproximateNotEqualingEvaluated() {
        assertThatThrownBy(() -> new DataQualityReport(
                10, 5, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0,
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-02T00:00:00Z"), "note"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankNote() {
        assertThatThrownBy(() -> new DataQualityReport(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, null, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
