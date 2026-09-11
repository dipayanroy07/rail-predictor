package com.railpredictor.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.ConfidenceProperties;
import com.railpredictor.config.ConfidenceThresholds;
import com.railpredictor.config.ConfidenceWeights;
import com.railpredictor.config.EvaluationCalibrationProperties;
import com.railpredictor.config.HistoricalAdjustmentProperties;
import com.railpredictor.model.domain.CalibrationEvaluationReport;
import com.railpredictor.model.domain.CalibrationStatus;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.DataQualityReport;
import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.PredictionEvaluationStatus;
import com.railpredictor.model.domain.PredictionSnapshot;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalibrationEvaluationReportBuilderTest {

    private static CalibrationEvaluationReportBuilder builder(int minimumSampleCount) {
        EvaluationCalibrationProperties calibrationProperties =
                new EvaluationCalibrationProperties(true, minimumSampleCount, 0.3, 5.0);
        PredictionAccuracyReportBuilder accuracyReportBuilder =
                new PredictionAccuracyReportBuilder(new PredictionAccuracyCalculator());
        ConfidenceProperties confidenceProperties = new ConfidenceProperties(
                new ConfidenceWeights(15, 10, 10, 10, 10, 10, 10), new ConfidenceThresholds(80, 60, 40), 2);

        return new CalibrationEvaluationReportBuilder(
                new WeatherContributionEvaluator(accuracyReportBuilder, calibrationProperties),
                new SimulationContributionEvaluator(accuracyReportBuilder, calibrationProperties),
                new ConfidenceCalibrationAuditor(
                        confidenceProperties, calibrationProperties, new PredictionAccuracyCalculator(), new ErrorDistributionCalculator()),
                new HistoricalWeightCalibrationAssessor(
                        new ChronologicalSplitter(), new HistoricalAdjustmentProperties(0.3, 5), calibrationProperties),
                new DisruptionImpactCalibrationAssessor(calibrationProperties));
    }

    private static PredictionSnapshot evaluated() {
        return new PredictionSnapshot(
                1L, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.EVALUATED_EXACT, 6, 2, Instant.parse("2026-09-09T11:00:00Z"));
    }

    private static PredictionSnapshot pending() {
        return new PredictionSnapshot(
                2L, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.PENDING, null, null, null);
    }

    @Test
    void overallStatusIsInsufficientDataGivenTodaysStructuralBlockers() {
        DataQualityReport dataQuality = DataQualityReport.empty("no data");

        CalibrationEvaluationReport report = builder(30).build(List.of(evaluated(), pending()), dataQuality);

        assertThat(report.overallCalibrationStatus()).isEqualTo(CalibrationStatus.INSUFFICIENT_DATA);
        assertThat(report.historicalWeightCalibration().status()).isEqualTo(CalibrationStatus.INSUFFICIENT_DATA);
        assertThat(report.disruptionImpactCalibration().status()).isEqualTo(CalibrationStatus.INSUFFICIENT_DATA);
    }

    @Test
    void onlyEvaluatedSnapshotsFeedTheAblationAndConfidenceAnalyses() {
        DataQualityReport dataQuality = DataQualityReport.empty("no data");

        CalibrationEvaluationReport report = builder(30).build(List.of(evaluated(), pending()), dataQuality);

        int totalAcrossWeatherVariants = report.byWeatherAvailability().values().stream()
                .mapToInt(v -> v.slice().sampleCount()).sum();
        assertThat(totalAcrossWeatherVariants).isEqualTo(1); // only the one evaluated snapshot, never the pending one
    }

    @Test
    void dataQualityIsPassedThroughUnchanged() {
        DataQualityReport dataQuality = new DataQualityReport(
                5, 3, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-02T00:00:00Z"), "note");

        CalibrationEvaluationReport report = builder(30).build(List.of(evaluated()), dataQuality);

        assertThat(report.dataQuality()).isEqualTo(dataQuality);
    }
}
