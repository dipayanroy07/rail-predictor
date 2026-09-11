package com.railpredictor.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.evaluation.PredictionAccuracyReportBuilder;
import com.railpredictor.evaluation.PredictionAccuracyCalculator;
import com.railpredictor.model.domain.AblationVariantResult;
import com.railpredictor.model.domain.CalibrationAssessment;
import com.railpredictor.model.domain.CalibrationBlockerReason;
import com.railpredictor.model.domain.CalibrationEvaluationReport;
import com.railpredictor.model.domain.CalibrationStatus;
import com.railpredictor.model.domain.ConfidenceBucketMetrics;
import com.railpredictor.model.domain.ConfidenceCalibrationReport;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.DataQualityReport;
import com.railpredictor.model.domain.ErrorDistribution;
import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.PredictionAccuracyReport;
import com.railpredictor.model.domain.PredictionAccuracySlice;
import com.railpredictor.model.domain.PredictionEvaluationStatus;
import com.railpredictor.model.domain.PredictionSnapshot;
import com.railpredictor.model.enums.ConfidenceLevel;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AccuracyReportMapperTest {

    private final AccuracyReportMapper mapper = new AccuracyReportMapper();
    private final PredictionAccuracyReportBuilder builder =
            new PredictionAccuracyReportBuilder(new PredictionAccuracyCalculator());

    private static PredictionSnapshot evaluated() {
        return new PredictionSnapshot(
                1L, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.EVALUATED_EXACT, 6, 2, Instant.parse("2026-09-09T11:00:00Z"));
    }

    @Test
    void disabledResponseHasEverythingElseNull() {
        AccuracyReportResponse response = mapper.toDisabledResponse();

        assertThat(response.evaluationEnabled()).isFalse();
        assertThat(response.overall()).isNull();
        assertThat(response.exactOnly()).isNull();
        assertThat(response.bySource()).isNull();
        assertThat(response.byProvenance()).isNull();
    }

    @Test
    void enabledResponseMirrorsTheDomainReport() {
        PredictionAccuracyReport report = builder.buildReport(List.of(evaluated()));

        AccuracyReportResponse response = mapper.toEnabledResponse(report);

        assertThat(response.evaluationEnabled()).isTrue();
        assertThat(response.overall().sampleCount()).isEqualTo(report.overall().sampleCount());
        assertThat(response.overall().exactCount()).isEqualTo(report.overall().exactCount());
        assertThat(response.overall().comparison().currentModel().bias())
                .isEqualTo(report.overall().comparison().currentModel().bias());
        assertThat(response.exactOnly().currentModel().sampleCount())
                .isEqualTo(report.exactOnly().currentModel().sampleCount());
        assertThat(response.bySource()).containsKey(HistoricalAdjustmentSource.STATION_FALLBACK);
        assertThat(response.byProvenance()).containsKey(DataProvenance.RAILRADAR);
    }

    @Test
    void enabledResponseWithNoCalibrationEvaluationLeavesItNull() {
        PredictionAccuracyReport report = builder.buildReport(List.of(evaluated()));

        AccuracyReportResponse response = mapper.toEnabledResponse(report, null);

        assertThat(response.calibrationEvaluation()).isNull();
    }

    @Test
    void enabledResponseWithACalibrationEvaluationMapsEveryField() {
        PredictionAccuracyReport report = builder.buildReport(List.of(evaluated()));
        PredictionAccuracySlice slice = builder.buildReport(List.of(evaluated())).overall();

        CalibrationEvaluationReport calibrationEvaluation = new CalibrationEvaluationReport(
                DataQualityReport.empty("note"),
                Map.of("WITH_WEATHER", new AblationVariantResult("WITH_WEATHER", slice, false)),
                Map.of("WITH_SIMULATION_CONTRIBUTION", new AblationVariantResult("WITH_SIMULATION_CONTRIBUTION", slice, false)),
                new ConfidenceCalibrationReport(
                        List.of(new ConfidenceBucketMetrics(ConfidenceLevel.HIGH, 1, false, 2.0, new ErrorDistribution(1, 2.0, 2.0))),
                        false, "not enough sufficient buckets"),
                CalibrationAssessment.blocked("prediction.historical-adjustment.weight",
                        CalibrationBlockerReason.METRIC_STRUCTURALLY_UNAFFECTED, "structurally blocked", 1),
                CalibrationAssessment.blocked("railway-disruption-impact.*",
                        CalibrationBlockerReason.METRIC_STRUCTURALLY_UNAFFECTED, "structurally blocked", 1),
                CalibrationStatus.INSUFFICIENT_DATA);

        AccuracyReportResponse response = mapper.toEnabledResponse(report, calibrationEvaluation);

        assertThat(response.calibrationEvaluation()).isNotNull();
        assertThat(response.calibrationEvaluation().overallCalibrationStatus()).isEqualTo(CalibrationStatus.INSUFFICIENT_DATA);
        assertThat(response.calibrationEvaluation().byWeatherAvailability()).containsKey("WITH_WEATHER");
        assertThat(response.calibrationEvaluation().bySimulationContribution()).containsKey("WITH_SIMULATION_CONTRIBUTION");
        assertThat(response.calibrationEvaluation().confidenceAnalysis().monotonic()).isFalse();
        assertThat(response.calibrationEvaluation().historicalWeightCalibration().blockerReason())
                .isEqualTo(CalibrationBlockerReason.METRIC_STRUCTURALLY_UNAFFECTED);
        assertThat(response.calibrationEvaluation().dataQuality().pointInTimeReproducibilityNote()).isEqualTo("note");
    }
}
