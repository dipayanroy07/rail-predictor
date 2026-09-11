package com.railpredictor.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

class WeatherContributionEvaluatorTest {

    private final WeatherContributionEvaluator evaluator = new WeatherContributionEvaluator(
            new PredictionAccuracyReportBuilder(new PredictionAccuracyCalculator()),
            new EvaluationCalibrationProperties(true, 3, 0.3, 5.0));

    private static PredictionSnapshot evaluated(String weatherProvenance, int actualDelayMinutes) {
        int predicted = 8;
        return new PredictionSnapshot(
                1L, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, predicted, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.EVALUATED_EXACT, actualDelayMinutes, predicted - actualDelayMinutes,
                Instant.parse("2026-09-09T11:00:00Z"), com.railpredictor.model.domain.PredictionEvaluationMode.LIVE_EVALUATION,
                weatherProvenance);
    }

    @Test
    void partitionsBySnapshotsWeatherProvenancePresence() {
        List<PredictionSnapshot> snapshots = List.of(
                evaluated("open-meteo", 8), evaluated("open-meteo", 9), evaluated("open-meteo", 8),
                evaluated(null, 6), evaluated(null, 5));

        Map<String, AblationVariantResult> result = evaluator.evaluate(snapshots);

        assertThat(result.get("WITH_WEATHER").slice().sampleCount()).isEqualTo(3);
        assertThat(result.get("WITHOUT_WEATHER").slice().sampleCount()).isEqualTo(2);
    }

    @Test
    void marksVariantInsufficientBelowConfiguredMinimum() {
        List<PredictionSnapshot> snapshots = List.of(evaluated("open-meteo", 8), evaluated(null, 6));

        Map<String, AblationVariantResult> result = evaluator.evaluate(snapshots);

        assertThat(result.get("WITH_WEATHER").sufficientSample()).isFalse(); // 1 < minimumSampleCount(3)
        assertThat(result.get("WITHOUT_WEATHER").sufficientSample()).isFalse();
    }

    @Test
    void marksVariantSufficientAtOrAboveConfiguredMinimum() {
        List<PredictionSnapshot> snapshots = List.of(
                evaluated("open-meteo", 8), evaluated("open-meteo", 7), evaluated("open-meteo", 9));

        Map<String, AblationVariantResult> result = evaluator.evaluate(snapshots);

        assertThat(result.get("WITH_WEATHER").sufficientSample()).isTrue();
    }

    @Test
    void rejectsUnevaluatedSnapshots() {
        PredictionSnapshot pending = new PredictionSnapshot(
                1L, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.PENDING, null, null, null);

        assertThatThrownBy(() -> evaluator.evaluate(List.of(pending))).isInstanceOf(IllegalArgumentException.class);
    }
}
