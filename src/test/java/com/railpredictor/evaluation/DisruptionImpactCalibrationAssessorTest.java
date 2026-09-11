package com.railpredictor.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.EvaluationCalibrationProperties;
import com.railpredictor.model.domain.CalibrationAssessment;
import com.railpredictor.model.domain.CalibrationBlockerReason;
import com.railpredictor.model.domain.CalibrationStatus;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.PredictionEvaluationMode;
import com.railpredictor.model.domain.PredictionEvaluationStatus;
import com.railpredictor.model.domain.PredictionSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DisruptionImpactCalibrationAssessorTest {

    private static PredictionSnapshot evaluated(Integer disruptionImpactMinutes) {
        return new PredictionSnapshot(
                1L, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.EVALUATED_EXACT, 6, 2, Instant.parse("2026-09-09T11:00:00Z"),
                PredictionEvaluationMode.LIVE_EVALUATION, null, disruptionImpactMinutes);
    }

    @Test
    void reportsInsufficientSampleSizeBelowConfiguredMinimum() {
        DisruptionImpactCalibrationAssessor assessor =
                new DisruptionImpactCalibrationAssessor(new EvaluationCalibrationProperties(true, 30, 0.3, 5.0));
        List<PredictionSnapshot> snapshots = List.of(evaluated(5), evaluated(0), evaluated(null));

        CalibrationAssessment assessment = assessor.assess(snapshots);

        assertThat(assessment.status()).isEqualTo(CalibrationStatus.INSUFFICIENT_DATA);
        assertThat(assessment.blockerReason()).isEqualTo(CalibrationBlockerReason.INSUFFICIENT_SAMPLE_SIZE);
        assertThat(assessment.realSampleCount()).isEqualTo(3);
    }

    @Test
    void reportsMockDataOnlyWhenSampleSizeIsSufficientButProvenanceCannotBeVerified() {
        DisruptionImpactCalibrationAssessor assessor =
                new DisruptionImpactCalibrationAssessor(new EvaluationCalibrationProperties(true, 3, 0.3, 5.0));
        List<PredictionSnapshot> snapshots = IntStream.range(0, 5).mapToObj(i -> evaluated(5)).toList();

        CalibrationAssessment assessment = assessor.assess(snapshots);

        assertThat(assessment.status()).isEqualTo(CalibrationStatus.INSUFFICIENT_DATA);
        assertThat(assessment.blockerReason()).isEqualTo(CalibrationBlockerReason.MOCK_DATA_ONLY);
    }

    @Test
    void neverProducesACandidateValue() {
        DisruptionImpactCalibrationAssessor assessor =
                new DisruptionImpactCalibrationAssessor(new EvaluationCalibrationProperties(true, 30, 0.3, 5.0));

        CalibrationAssessment assessment = assessor.assess(List.of(evaluated(5)));

        assertThat(assessment.selectedCandidateValue()).isNull();
        assertThat(assessment.validationImprovementPercent()).isNull();
    }
}
