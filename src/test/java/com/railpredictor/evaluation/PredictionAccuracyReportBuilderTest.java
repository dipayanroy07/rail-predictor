package com.railpredictor.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.PredictionAccuracyComparison;
import com.railpredictor.model.domain.PredictionAccuracyReport;
import com.railpredictor.model.domain.PredictionAccuracySlice;
import com.railpredictor.model.domain.PredictionEvaluationStatus;
import com.railpredictor.model.domain.PredictionSnapshot;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PredictionAccuracyReportBuilderTest {

    private final PredictionAccuracyReportBuilder builder = new PredictionAccuracyReportBuilder(new PredictionAccuracyCalculator());

    private static PredictionSnapshot evaluated(
            int currentDelayMinutes, int predictedNextStationDelayMinutes, int actualDelayMinutes,
            PredictionEvaluationStatus status) {
        return evaluated(currentDelayMinutes, predictedNextStationDelayMinutes, actualDelayMinutes, status,
                HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR);
    }

    private static PredictionSnapshot evaluated(
            int currentDelayMinutes, int predictedNextStationDelayMinutes, int actualDelayMinutes,
            PredictionEvaluationStatus status, HistoricalAdjustmentSource source, String provenance) {
        int error = predictedNextStationDelayMinutes - actualDelayMinutes;
        return new PredictionSnapshot(
                1L, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                currentDelayMinutes, predictedNextStationDelayMinutes, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, source, provenance,
                75.0, status, actualDelayMinutes, error, Instant.parse("2026-09-09T11:00:00Z"));
    }

    private static PredictionSnapshot quarantinedEvaluated(
            int currentDelayMinutes, int predictedNextStationDelayMinutes, int actualDelayMinutes,
            PredictionEvaluationStatus status) {
        int error = predictedNextStationDelayMinutes - actualDelayMinutes;
        return new PredictionSnapshot(
                3L, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                currentDelayMinutes, predictedNextStationDelayMinutes, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, status, actualDelayMinutes, error, Instant.parse("2026-09-09T11:00:00Z"),
                com.railpredictor.model.domain.PredictionEvaluationMode.LIVE_EVALUATION, null, null,
                0, HistoricalAdjustmentSource.NONE, DataProvenance.UNAVAILABLE, 0,
                true, "matched against a RailRadar upcoming-stop placeholder (Phase 22D/22C)");
    }

    private static PredictionSnapshot pending() {
        return new PredictionSnapshot(
                2L, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.PENDING, null, null, null);
    }

    @Test
    void computesCurrentModelAndBaselineErrorsSeparately() {
        // current model predicted 8, current-delay-only baseline would have predicted 5 (the
        // stored currentDelayMinutes); actual turned out to be 6.
        PredictionSnapshot snapshot = evaluated(5, 8, 6, PredictionEvaluationStatus.EVALUATED_EXACT);

        PredictionAccuracyComparison comparison = builder.build(List.of(snapshot));

        assertThat(comparison.currentModel().sampleCount()).isEqualTo(1);
        assertThat(comparison.currentModel().bias()).isEqualTo(2.0); // 8 - 6
        assertThat(comparison.baseline().bias()).isEqualTo(-1.0); // 5 - 6
    }

    @Test
    void pendingSnapshotsAreExcludedFromBothMetrics() {
        PredictionAccuracyComparison comparison = builder.build(List.of(pending()));

        assertThat(comparison.currentModel().sampleCount()).isZero();
        assertThat(comparison.baseline().sampleCount()).isZero();
    }

    @Test
    void approximateEvaluationsStillContribute() {
        PredictionSnapshot snapshot = evaluated(5, 8, 6, PredictionEvaluationStatus.EVALUATED_APPROXIMATE);

        PredictionAccuracyComparison comparison = builder.build(List.of(snapshot));

        assertThat(comparison.currentModel().sampleCount()).isEqualTo(1);
    }

    @Test
    void emptyInputProducesEmptyMetricsForBoth() {
        PredictionAccuracyComparison comparison = builder.build(List.of());

        assertThat(comparison.currentModel().sampleCount()).isZero();
        assertThat(comparison.baseline().sampleCount()).isZero();
    }

    @Test
    void multipleSnapshotsAggregateAcrossBoth() {
        PredictionSnapshot a = evaluated(5, 8, 6, PredictionEvaluationStatus.EVALUATED_EXACT);
        PredictionSnapshot b = evaluated(10, 12, 15, PredictionEvaluationStatus.EVALUATED_EXACT);

        PredictionAccuracyComparison comparison = builder.build(List.of(a, b));

        assertThat(comparison.currentModel().sampleCount()).isEqualTo(2);
        assertThat(comparison.baseline().sampleCount()).isEqualTo(2);
    }

    @Test
    void buildReportCombinesExactAndApproximateIntoOverallButExposesTheSplit() {
        PredictionSnapshot exact = evaluated(5, 8, 6, PredictionEvaluationStatus.EVALUATED_EXACT);
        PredictionSnapshot approximate = evaluated(10, 12, 15, PredictionEvaluationStatus.EVALUATED_APPROXIMATE);

        PredictionAccuracyReport report = builder.buildReport(List.of(exact, approximate));

        assertThat(report.overall().sampleCount()).isEqualTo(2);
        assertThat(report.overall().exactCount()).isEqualTo(1);
        assertThat(report.overall().approximateCount()).isEqualTo(1);
    }

    @Test
    void buildReportExcludesPendingSnapshotsFromEveryBreakdown() {
        PredictionAccuracyReport report = builder.buildReport(List.of(pending()));

        assertThat(report.overall().sampleCount()).isZero();
        assertThat(report.bySource().get(HistoricalAdjustmentSource.STATION_FALLBACK).sampleCount()).isZero();
    }

    @Test
    void exactOnlyExcludesApproximateEvaluationsEvenThoughOverallIncludesThem() {
        PredictionSnapshot exact = evaluated(5, 8, 6, PredictionEvaluationStatus.EVALUATED_EXACT);
        PredictionSnapshot approximate = evaluated(10, 12, 15, PredictionEvaluationStatus.EVALUATED_APPROXIMATE);

        PredictionAccuracyReport report = builder.buildReport(List.of(exact, approximate));

        assertThat(report.overall().sampleCount()).isEqualTo(2);
        assertThat(report.exactOnly().currentModel().sampleCount()).isEqualTo(1);
    }

    @Test
    void bySourceAlwaysExposesAllThreeSourcesEvenWithZeroSamples() {
        PredictionSnapshot stationFallback = evaluated(
                5, 8, 6, PredictionEvaluationStatus.EVALUATED_EXACT,
                HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR);

        PredictionAccuracyReport report = builder.buildReport(List.of(stationFallback));

        assertThat(report.bySource()).containsOnlyKeys(
                HistoricalAdjustmentSource.SECTION, HistoricalAdjustmentSource.STATION_FALLBACK, HistoricalAdjustmentSource.NONE);
        assertThat(report.bySource().get(HistoricalAdjustmentSource.STATION_FALLBACK).sampleCount()).isEqualTo(1);
        assertThat(report.bySource().get(HistoricalAdjustmentSource.SECTION).sampleCount()).isZero();
    }

    @Test
    void byProvenanceSeparatesMockFromRealDataAndNeverMergesThem() {
        PredictionSnapshot real = evaluated(
                5, 8, 6, PredictionEvaluationStatus.EVALUATED_EXACT,
                HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR);
        PredictionSnapshot mock = evaluated(
                5, 8, 6, PredictionEvaluationStatus.EVALUATED_EXACT,
                HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.MOCK);

        PredictionAccuracyReport report = builder.buildReport(List.of(real, mock));

        assertThat(report.byProvenance()).containsOnlyKeys(DataProvenance.RAILRADAR, DataProvenance.MOCK);
        assertThat(report.byProvenance().get(DataProvenance.RAILRADAR).sampleCount()).isEqualTo(1);
        assertThat(report.byProvenance().get(DataProvenance.MOCK).sampleCount()).isEqualTo(1);
    }

    @Test
    void improvementPercentIsPositiveWhenTheModelBeatsTheBaseline() {
        // model error magnitude 1 (8-7), baseline error magnitude 3 (5-7): model clearly better.
        PredictionSnapshot snapshot = evaluated(5, 8, 7, PredictionEvaluationStatus.EVALUATED_EXACT);

        PredictionAccuracyReport report = builder.buildReport(List.of(snapshot));

        assertThat(report.overall().improvementPercent()).isNotNull();
        assertThat(report.overall().improvementPercent()).isGreaterThan(0.0);
    }

    @Test
    void improvementPercentIsNegativeWhenTheBaselineBeatsTheModel() {
        // model error magnitude 3 (8-11? use predicted 8 actual 2 -> error 6), baseline error magnitude small.
        PredictionSnapshot snapshot = evaluated(5, 12, 6, PredictionEvaluationStatus.EVALUATED_EXACT);

        PredictionAccuracyReport report = builder.buildReport(List.of(snapshot));

        assertThat(report.overall().improvementPercent()).isNotNull();
        assertThat(report.overall().improvementPercent()).isLessThan(0.0);
    }

    @Test
    void improvementPercentIsNullWhenBaselineMaeIsZero() {
        // currentDelayMinutes equals actualDelayMinutes -> baseline error is exactly zero.
        PredictionSnapshot snapshot = evaluated(6, 8, 6, PredictionEvaluationStatus.EVALUATED_EXACT);

        PredictionAccuracyReport report = builder.buildReport(List.of(snapshot));

        assertThat(report.overall().improvementPercent()).isNull();
    }

    @Test
    void buildReportOnEmptyInputProducesAllZeroSlicesAndNoProvenanceKeys() {
        PredictionAccuracyReport report = builder.buildReport(List.of());

        assertThat(report.overall().sampleCount()).isZero();
        assertThat(report.exactOnly().currentModel().sampleCount()).isZero();
        assertThat(report.byProvenance()).isEmpty();
        assertThat(report.bySource()).hasSize(3);
        assertThat(report.byEvaluationMode()).hasSize(2);
        assertThat(report.byEvaluationMode().get(com.railpredictor.model.domain.PredictionEvaluationMode.LIVE_EVALUATION)
                .sampleCount()).isZero();
    }

    @Test
    void byEvaluationModeAlwaysExposesBothModesEvenWhenOnlyLiveEvaluationHasData() {
        PredictionSnapshot live = evaluated(5, 8, 6, PredictionEvaluationStatus.EVALUATED_EXACT);

        PredictionAccuracyReport report = builder.buildReport(List.of(live));

        assertThat(report.byEvaluationMode()).containsOnlyKeys(
                com.railpredictor.model.domain.PredictionEvaluationMode.LIVE_EVALUATION,
                com.railpredictor.model.domain.PredictionEvaluationMode.HISTORICAL_BACKTEST);
        assertThat(report.byEvaluationMode().get(com.railpredictor.model.domain.PredictionEvaluationMode.LIVE_EVALUATION)
                .sampleCount()).isEqualTo(1);
        assertThat(report.byEvaluationMode().get(com.railpredictor.model.domain.PredictionEvaluationMode.HISTORICAL_BACKTEST)
                .sampleCount()).isZero();
    }

    @Test
    void quarantinedSnapshotsAreExcludedFromBuild() {
        PredictionSnapshot quarantined = quarantinedEvaluated(5, 8, 6, PredictionEvaluationStatus.EVALUATED_EXACT);

        PredictionAccuracyComparison comparison = builder.build(List.of(quarantined));

        assertThat(comparison.currentModel().sampleCount()).isZero();
        assertThat(comparison.baseline().sampleCount()).isZero();
    }

    @Test
    void quarantinedSnapshotsAreExcludedFromBuildReportEveryBreakdown() {
        PredictionSnapshot quarantined = quarantinedEvaluated(5, 8, 6, PredictionEvaluationStatus.EVALUATED_EXACT);
        PredictionSnapshot valid = evaluated(5, 8, 6, PredictionEvaluationStatus.EVALUATED_EXACT);

        PredictionAccuracyReport report = builder.buildReport(List.of(quarantined, valid));

        assertThat(report.overall().sampleCount()).isEqualTo(1);
        assertThat(report.exactOnly().currentModel().sampleCount()).isEqualTo(1);
        assertThat(report.bySource().get(HistoricalAdjustmentSource.STATION_FALLBACK).sampleCount()).isEqualTo(1);
    }
}
