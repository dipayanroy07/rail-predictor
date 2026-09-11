package com.railpredictor.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.ConfidenceProperties;
import com.railpredictor.config.ConfidenceThresholds;
import com.railpredictor.config.ConfidenceWeights;
import com.railpredictor.config.EvaluationCalibrationProperties;
import com.railpredictor.model.domain.ConfidenceBucketMetrics;
import com.railpredictor.model.domain.ConfidenceCalibrationReport;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.PredictionEvaluationStatus;
import com.railpredictor.model.domain.PredictionSnapshot;
import com.railpredictor.model.enums.ConfidenceLevel;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfidenceCalibrationAuditorTest {

    private static final ConfidenceThresholds THRESHOLDS = new ConfidenceThresholds(80.0, 60.0, 40.0);

    private ConfidenceCalibrationAuditor auditorWithMinimumSample(int minimumSampleCount) {
        ConfidenceWeights weights = new ConfidenceWeights(15, 10, 10, 10, 10, 10, 10);
        ConfidenceProperties confidenceProperties = new ConfidenceProperties(weights, THRESHOLDS, 2);
        return new ConfidenceCalibrationAuditor(
                confidenceProperties,
                new EvaluationCalibrationProperties(true, minimumSampleCount, 0.3, 5.0),
                new PredictionAccuracyCalculator(),
                new ErrorDistributionCalculator());
    }

    private static PredictionSnapshot evaluated(double confidenceScore, int errorMinutes) {
        int predicted = 8;
        int actual = predicted - errorMinutes;
        return new PredictionSnapshot(
                1L, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, predicted, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                confidenceScore, PredictionEvaluationStatus.EVALUATED_EXACT, actual, errorMinutes,
                Instant.parse("2026-09-09T11:00:00Z"));
    }

    @Test
    void bucketsSnapshotsByConfigurredThresholds() {
        List<PredictionSnapshot> snapshots = List.of(
                evaluated(90, 1), evaluated(70, 3), evaluated(50, 6), evaluated(20, 10));

        ConfidenceCalibrationReport report = auditorWithMinimumSample(1).audit(snapshots);

        ConfidenceBucketMetrics high = bucket(report, ConfidenceLevel.HIGH);
        ConfidenceBucketMetrics medium = bucket(report, ConfidenceLevel.MEDIUM);
        ConfidenceBucketMetrics low = bucket(report, ConfidenceLevel.LOW);
        ConfidenceBucketMetrics veryLow = bucket(report, ConfidenceLevel.VERY_LOW);

        assertThat(high.sampleCount()).isEqualTo(1);
        assertThat(medium.sampleCount()).isEqualTo(1);
        assertThat(low.sampleCount()).isEqualTo(1);
        assertThat(veryLow.sampleCount()).isEqualTo(1);
    }

    @Test
    void reportsInsufficientWhenFewerThanTwoBucketsHaveEnoughSamples() {
        List<PredictionSnapshot> snapshots = List.of(evaluated(90, 1), evaluated(90, 2));

        ConfidenceCalibrationReport report = auditorWithMinimumSample(5).audit(snapshots);

        assertThat(report.monotonic()).isFalse();
        assertThat(report.explanation()).containsIgnoringCase("sufficient sample");
    }

    @Test
    void detectsMonotonicErrorIncreaseAsConfidenceDrops() {
        List<PredictionSnapshot> snapshots = List.of(
                evaluated(90, 1), evaluated(90, 1), evaluated(90, 1),
                evaluated(20, 10), evaluated(20, 12), evaluated(20, 8));

        ConfidenceCalibrationReport report = auditorWithMinimumSample(3).audit(snapshots);

        assertThat(report.monotonic()).isTrue();
    }

    @Test
    void detectsNonMonotonicErrorWhenLowerConfidenceIsMoreAccurate() {
        List<PredictionSnapshot> snapshots = List.of(
                evaluated(90, 10), evaluated(90, 12), evaluated(90, 8),
                evaluated(20, 1), evaluated(20, 1), evaluated(20, 1));

        ConfidenceCalibrationReport report = auditorWithMinimumSample(3).audit(snapshots);

        assertThat(report.monotonic()).isFalse();
    }

    private static ConfidenceBucketMetrics bucket(ConfidenceCalibrationReport report, ConfidenceLevel level) {
        return report.buckets().stream().filter(b -> b.level() == level).findFirst().orElseThrow();
    }
}
