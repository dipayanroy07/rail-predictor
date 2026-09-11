package com.railpredictor.model.dto;

import com.railpredictor.model.domain.AblationVariantResult;
import com.railpredictor.model.domain.CalibrationAssessment;
import com.railpredictor.model.domain.CalibrationEvaluationReport;
import com.railpredictor.model.domain.ConfidenceBucketMetrics;
import com.railpredictor.model.domain.ConfidenceCalibrationReport;
import com.railpredictor.model.domain.DataQualityReport;
import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.PredictionAccuracyComparison;
import com.railpredictor.model.domain.PredictionAccuracyMetrics;
import com.railpredictor.model.domain.PredictionAccuracyReport;
import com.railpredictor.model.domain.PredictionAccuracySlice;
import com.railpredictor.model.domain.PredictionEvaluationMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Translates the internal {@link PredictionAccuracyReport} into the external
 * {@link AccuracyReportResponse} JSON shape (Phase 16H-6) - the one place that shape is decided,
 * mirroring {@link PredictionOutputMapper}'s role for the prediction endpoint. Never exposes
 * {@code PredictionSnapshot} or any JPA entity directly.
 */
@Component
public class AccuracyReportMapper {

    /** The explicit "evaluation is disabled" response - see {@link AccuracyReportResponse}'s own
     * Javadoc for why this is never a fabricated empty report instead. */
    public AccuracyReportResponse toDisabledResponse() {
        return new AccuracyReportResponse(false, null, null, null, null, null);
    }

    public AccuracyReportResponse toEnabledResponse(PredictionAccuracyReport report) {
        return toEnabledResponse(report, null);
    }

    /** @param calibrationEvaluation {@code null} when {@code evaluation.calibration.enabled=false}. */
    public AccuracyReportResponse toEnabledResponse(
            PredictionAccuracyReport report, CalibrationEvaluationReport calibrationEvaluation) {
        Objects.requireNonNull(report, "report");

        Map<HistoricalAdjustmentSource, AccuracySliceResponse> bySource = report.bySource().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> toSliceResponse(e.getValue())));
        Map<String, AccuracySliceResponse> byProvenance = report.byProvenance().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, e -> toSliceResponse(e.getValue()),
                        (a, b) -> a, java.util.LinkedHashMap::new));
        Map<PredictionEvaluationMode, AccuracySliceResponse> byEvaluationMode = report.byEvaluationMode().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> toSliceResponse(e.getValue())));

        return new AccuracyReportResponse(
                true,
                toSliceResponse(report.overall()),
                toComparisonResponse(report.exactOnly()),
                bySource,
                byProvenance,
                byEvaluationMode,
                calibrationEvaluation == null ? null : toCalibrationEvaluationResponse(calibrationEvaluation));
    }

    private static CalibrationEvaluationResponse toCalibrationEvaluationResponse(CalibrationEvaluationReport report) {
        Map<String, AblationVariantResponse> byWeather = new LinkedHashMap<>();
        report.byWeatherAvailability().forEach((key, value) -> byWeather.put(key, toAblationVariantResponse(value)));

        Map<String, AblationVariantResponse> bySimulation = new LinkedHashMap<>();
        report.bySimulationContribution().forEach((key, value) -> bySimulation.put(key, toAblationVariantResponse(value)));

        return new CalibrationEvaluationResponse(
                toDataQualityResponse(report.dataQuality()),
                byWeather,
                bySimulation,
                toConfidenceCalibrationResponse(report.confidenceAnalysis()),
                toCalibrationAssessmentResponse(report.historicalWeightCalibration()),
                toCalibrationAssessmentResponse(report.disruptionImpactCalibration()),
                report.overallCalibrationStatus());
    }

    private static AblationVariantResponse toAblationVariantResponse(AblationVariantResult result) {
        return new AblationVariantResponse(result.variantName(), toSliceResponse(result.slice()), result.sufficientSample());
    }

    private static DataQualityResponse toDataQualityResponse(DataQualityReport report) {
        return new DataQualityResponse(
                report.totalSnapshots(),
                report.evaluatedSnapshots(),
                report.exactEvaluations(),
                report.approximateEvaluations(),
                report.pendingSnapshots(),
                report.notEvaluableSnapshots(),
                report.weatherAvailableCount(),
                report.historicalAvailableCount(),
                report.disruptionImpactAvailableCount(),
                report.simulationContributedCount(),
                report.distinctTrainCount(),
                report.distinctStationCount(),
                report.earliestPredictionMadeAt(),
                report.latestPredictionMadeAt(),
                report.pointInTimeReproducibilityNote(),
                report.historicalObservationCount());
    }

    private static ConfidenceCalibrationResponse toConfidenceCalibrationResponse(ConfidenceCalibrationReport report) {
        return new ConfidenceCalibrationResponse(
                report.buckets().stream().map(AccuracyReportMapper::toConfidenceBucketResponse).toList(),
                report.monotonic(),
                report.explanation());
    }

    private static ConfidenceBucketResponse toConfidenceBucketResponse(ConfidenceBucketMetrics bucket) {
        return new ConfidenceBucketResponse(
                bucket.level(),
                bucket.sampleCount(),
                bucket.sufficientSample(),
                bucket.meanAbsoluteError(),
                bucket.errorDistribution().medianAbsoluteError(),
                bucket.errorDistribution().p90AbsoluteError());
    }

    private static CalibrationAssessmentResponse toCalibrationAssessmentResponse(CalibrationAssessment assessment) {
        return new CalibrationAssessmentResponse(
                assessment.parameterName(),
                assessment.status(),
                assessment.blockerReason(),
                assessment.explanation(),
                assessment.realSampleCount(),
                assessment.trainingSampleCount(),
                assessment.validationSampleCount(),
                assessment.currentValue(),
                assessment.selectedCandidateValue(),
                assessment.validationImprovementPercent());
    }

    private static AccuracySliceResponse toSliceResponse(PredictionAccuracySlice slice) {
        return new AccuracySliceResponse(
                slice.sampleCount(),
                slice.exactCount(),
                slice.approximateCount(),
                toComparisonResponse(slice.comparison()),
                slice.improvementPercent());
    }

    private static AccuracyComparisonResponse toComparisonResponse(PredictionAccuracyComparison comparison) {
        return new AccuracyComparisonResponse(
                toMetricsResponse(comparison.currentModel()), toMetricsResponse(comparison.baseline()));
    }

    private static AccuracyMetricsResponse toMetricsResponse(PredictionAccuracyMetrics metrics) {
        return new AccuracyMetricsResponse(
                metrics.sampleCount(), metrics.meanAbsoluteError(), metrics.rootMeanSquaredError(), metrics.bias());
    }
}
