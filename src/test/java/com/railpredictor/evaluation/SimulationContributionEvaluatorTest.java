package com.railpredictor.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.EvaluationCalibrationProperties;
import com.railpredictor.model.domain.AblationVariantResult;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.PredictionEvaluationStatus;
import com.railpredictor.model.domain.PredictionSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimulationContributionEvaluatorTest {

    private final SimulationContributionEvaluator evaluator = new SimulationContributionEvaluator(
            new PredictionAccuracyReportBuilder(new PredictionAccuracyCalculator()),
            new EvaluationCalibrationProperties(true, 2, 0.3, 5.0));

    private static PredictionSnapshot evaluated(int currentDelayMinutes, int predictedNextStationDelayMinutes, int actualDelayMinutes) {
        return new PredictionSnapshot(
                1L, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                currentDelayMinutes, predictedNextStationDelayMinutes, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.EVALUATED_EXACT, actualDelayMinutes,
                predictedNextStationDelayMinutes - actualDelayMinutes, Instant.parse("2026-09-09T11:00:00Z"));
    }

    @Test
    void partitionsByDerivedSimulationContribution() {
        List<PredictionSnapshot> snapshots = List.of(
                evaluated(5, 5, 5),  // extra = 0 -> without
                evaluated(5, 5, 4),  // extra = 0 -> without
                evaluated(5, 9, 8),  // extra = 4 -> with
                evaluated(5, 12, 9)); // extra = 7 -> with

        Map<String, AblationVariantResult> result = evaluator.evaluate(snapshots);

        assertThat(result.get("WITHOUT_SIMULATION_CONTRIBUTION").slice().sampleCount()).isEqualTo(2);
        assertThat(result.get("WITH_SIMULATION_CONTRIBUTION").slice().sampleCount()).isEqualTo(2);
    }

    @Test
    void sufficientSampleGateUsesConfiguredMinimum() {
        List<PredictionSnapshot> snapshots = List.of(evaluated(5, 5, 5), evaluated(5, 9, 8));

        Map<String, AblationVariantResult> result = evaluator.evaluate(snapshots);

        assertThat(result.get("WITHOUT_SIMULATION_CONTRIBUTION").sufficientSample()).isFalse(); // 1 < 2
        assertThat(result.get("WITH_SIMULATION_CONTRIBUTION").sufficientSample()).isFalse();
    }
}
