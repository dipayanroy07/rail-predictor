package com.railpredictor.model.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class PredictionAccuracySliceTest {

    private static PredictionAccuracyComparison comparison() {
        return new PredictionAccuracyComparison(
                new PredictionAccuracyMetrics(2, 1.0, 1.5, 0.5),
                new PredictionAccuracyMetrics(2, 2.0, 2.5, 1.0));
    }

    @Test
    void countsMustSumToSampleCount() {
        assertThatCode(() -> new PredictionAccuracySlice(2, 1, 1, comparison(), 10.0))
                .doesNotThrowAnyException();
    }

    @Test
    void mismatchedCountsAreRejected() {
        assertThatThrownBy(() -> new PredictionAccuracySlice(2, 1, 2, comparison(), 10.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullComparisonIsRejected() {
        assertThatThrownBy(() -> new PredictionAccuracySlice(0, 0, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void improvementPercentMayBeNull() {
        assertThatCode(() -> new PredictionAccuracySlice(0, 0, 0, comparison(), null))
                .doesNotThrowAnyException();
    }
}
