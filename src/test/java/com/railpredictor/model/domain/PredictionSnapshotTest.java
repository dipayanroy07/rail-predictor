package com.railpredictor.model.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PredictionSnapshotTest {

    private static PredictionSnapshot pending() {
        return new PredictionSnapshot(
                null, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.PENDING, null, null, null);
    }

    @Test
    void pendingWithNoOutcomeFieldsIsValid() {
        assertDoesNotThrow(PredictionSnapshotTest::pending);
    }

    @Test
    void pendingWithAnActualDelaySetIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PredictionSnapshot(
                null, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.PENDING, 6, null, null));
    }

    @Test
    void evaluatedExactRequiresAllThreeOutcomeFields() {
        assertThrows(IllegalArgumentException.class, () -> new PredictionSnapshot(
                null, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.EVALUATED_EXACT, 6, null, Instant.parse("2026-09-09T11:00:00Z")));
    }

    @Test
    void evaluatedExactWithAllThreeOutcomeFieldsIsValid() {
        assertDoesNotThrow(() -> new PredictionSnapshot(
                1L, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.EVALUATED_EXACT, 6, 2, Instant.parse("2026-09-09T11:00:00Z")));
    }

    @Test
    void notEvaluableWithOutcomeFieldsSetIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PredictionSnapshot(
                null, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.NOT_EVALUABLE, 6, 2, Instant.parse("2026-09-09T11:00:00Z")));
    }

    @Test
    void rejectsABlankTrainNumber() {
        assertThrows(IllegalArgumentException.class, () -> new PredictionSnapshot(
                null, " ", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.PENDING, null, null, null));
    }

    // --- Phase 16H-7: evaluationMode ---

    @Test
    void theLegacyConstructorDefaultsEvaluationModeToLiveEvaluation() {
        assertThat(pending().evaluationMode()).isEqualTo(PredictionEvaluationMode.LIVE_EVALUATION);
    }

    @Test
    void theCanonicalConstructorAcceptsAnExplicitEvaluationMode() {
        PredictionSnapshot snapshot = new PredictionSnapshot(
                null, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.PENDING, null, null, null,
                PredictionEvaluationMode.HISTORICAL_BACKTEST);

        assertThat(snapshot.evaluationMode()).isEqualTo(PredictionEvaluationMode.HISTORICAL_BACKTEST);
    }

    @Test
    void rejectsANullEvaluationMode() {
        assertThrows(IllegalArgumentException.class, () -> new PredictionSnapshot(
                null, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.PENDING, null, null, null, null));
    }

    // --- Phase 17: weatherProvenance ---

    @Test
    void theLegacyConstructorsDefaultWeatherProvenanceToNull() {
        assertThat(pending().weatherProvenance()).isNull();
    }

    @Test
    void theCanonicalConstructorAcceptsAnExplicitWeatherProvenance() {
        PredictionSnapshot snapshot = new PredictionSnapshot(
                null, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.PENDING, null, null, null,
                PredictionEvaluationMode.LIVE_EVALUATION, DataProvenance.OPENMETEO);

        assertThat(snapshot.weatherProvenance()).isEqualTo(DataProvenance.OPENMETEO);
    }

    @Test
    void weatherProvenanceMayBeNullEvenOnTheCanonicalConstructor() {
        // Unlike historicalAdjustmentProvenance (always has a NONE/UNAVAILABLE fallback), weather
        // genuinely has no fallback constant - null is the honest "wasn't available" state.
        PredictionSnapshot snapshot = new PredictionSnapshot(
                null, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.PENDING, null, null, null,
                PredictionEvaluationMode.LIVE_EVALUATION, null);

        assertThat(snapshot.weatherProvenance()).isNull();
    }

    // --- Phase 19: disruptionImpactMinutes ---

    @Test
    void theLegacyConstructorsDefaultDisruptionImpactMinutesToNull() {
        assertThat(pending().disruptionImpactMinutes()).isNull();
    }

    @Test
    void theCanonicalConstructorAcceptsAnExplicitDisruptionImpactMinutes() {
        PredictionSnapshot snapshot = new PredictionSnapshot(
                null, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.PENDING, null, null, null,
                PredictionEvaluationMode.LIVE_EVALUATION, DataProvenance.OPENMETEO, 15);

        assertThat(snapshot.disruptionImpactMinutes()).isEqualTo(15);
    }

    @Test
    void disruptionImpactMinutesMayLegitimatelyBeZero() {
        PredictionSnapshot snapshot = new PredictionSnapshot(
                null, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.PENDING, null, null, null,
                PredictionEvaluationMode.LIVE_EVALUATION, null, 0);

        assertThat(snapshot.disruptionImpactMinutes()).isZero();
    }

    @Test
    void rejectsANegativeDisruptionImpactMinutes() {
        assertThrows(IllegalArgumentException.class, () -> new PredictionSnapshot(
                null, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.PENDING, null, null, null,
                PredictionEvaluationMode.LIVE_EVALUATION, null, -1));
    }
}
