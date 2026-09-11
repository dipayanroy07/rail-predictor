package com.railpredictor.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.EvaluationCalibrationProperties;
import com.railpredictor.config.HistoricalAdjustmentProperties;
import com.railpredictor.model.domain.CalibrationAssessment;
import com.railpredictor.model.domain.CalibrationBlockerReason;
import com.railpredictor.model.domain.CalibrationStatus;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.PredictionEvaluationStatus;
import com.railpredictor.model.domain.PredictionSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class HistoricalWeightCalibrationAssessorTest {

    private final HistoricalWeightCalibrationAssessor assessor = new HistoricalWeightCalibrationAssessor(
            new ChronologicalSplitter(),
            new HistoricalAdjustmentProperties(0.3, 5),
            new EvaluationCalibrationProperties(true, 30, 0.3, 5.0));

    private static PredictionSnapshot evaluated(Instant predictionMadeAt) {
        return new PredictionSnapshot(
                1L, "12952", predictionMadeAt, "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.EVALUATED_EXACT, 6, 2, Instant.parse("2026-09-09T11:00:00Z"));
    }

    @Test
    void reportsInsufficientSampleSizeNeverStructurallyUnaffectedOnceEnoughRealDataExists() {
        // Phase 21: historical adjustment now genuinely enters the evaluated metric, so a
        // sufficient sample must never be reported as structurally blocked - only as "no real
        // candidate-search algorithm exists yet" (still INSUFFICIENT_DATA, but for a data/algorithm
        // reason, never METRIC_STRUCTURALLY_UNAFFECTED).
        List<PredictionSnapshot> snapshots = IntStream.range(0, 100)
                .mapToObj(i -> evaluated(Instant.parse("2026-09-01T00:00:00Z").plusSeconds(i * 3600L)))
                .toList();

        CalibrationAssessment assessment = assessor.assess(snapshots);

        assertThat(assessment.status()).isEqualTo(CalibrationStatus.INSUFFICIENT_DATA);
        assertThat(assessment.blockerReason()).isEqualTo(CalibrationBlockerReason.INSUFFICIENT_SAMPLE_SIZE);
        assertThat(assessment.selectedCandidateValue()).isNull();
        assertThat(assessment.validationImprovementPercent()).isNull();
    }

    @Test
    void reportsInsufficientSampleSizeBelowConfiguredMinimum() {
        List<PredictionSnapshot> snapshots = List.of(evaluated(Instant.parse("2026-09-01T00:00:00Z")));

        CalibrationAssessment assessment = assessor.assess(snapshots);

        assertThat(assessment.status()).isEqualTo(CalibrationStatus.INSUFFICIENT_DATA);
        assertThat(assessment.blockerReason()).isEqualTo(CalibrationBlockerReason.INSUFFICIENT_SAMPLE_SIZE);
    }

    @Test
    void neverFabricatesACalibratedCoefficientEvenWithLargeSample() {
        List<PredictionSnapshot> snapshots = IntStream.range(0, 1000)
                .mapToObj(i -> evaluated(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i * 60L)))
                .toList();

        CalibrationAssessment assessment = assessor.assess(snapshots);

        assertThat(assessment.status()).isNotEqualTo(CalibrationStatus.PROVISIONALLY_CALIBRATED);
        assertThat(assessment.status()).isNotEqualTo(CalibrationStatus.VALIDATED);
    }

    @Test
    void reportsRealTrainingAndValidationSampleCountsForTransparencyOnceSampleSizeIsMet() {
        HistoricalWeightCalibrationAssessor lowThresholdAssessor = new HistoricalWeightCalibrationAssessor(
                new ChronologicalSplitter(),
                new HistoricalAdjustmentProperties(0.3, 5),
                new EvaluationCalibrationProperties(true, 5, 0.3, 5.0));
        List<PredictionSnapshot> snapshots = IntStream.range(0, 10)
                .mapToObj(i -> evaluated(Instant.parse("2026-09-01T00:00:00Z").plusSeconds(i * 3600L)))
                .toList();

        CalibrationAssessment assessment = lowThresholdAssessor.assess(snapshots);

        assertThat(assessment.realSampleCount()).isEqualTo(10);
        assertThat(assessment.trainingSampleCount()).isEqualTo(7);
        assertThat(assessment.validationSampleCount()).isEqualTo(3);
        assertThat(assessment.currentValue()).isEqualTo(0.3);
    }

    @Test
    void tooFewSamplesToEvenSplitStillReportsBlockedWithoutSplitCounts() {
        CalibrationAssessment assessment = assessor.assess(List.of(evaluated(Instant.parse("2026-09-01T00:00:00Z"))));

        assertThat(assessment.status()).isEqualTo(CalibrationStatus.INSUFFICIENT_DATA);
        assertThat(assessment.trainingSampleCount()).isNull();
        assertThat(assessment.validationSampleCount()).isNull();
    }
}
