package com.railpredictor.model.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PredictionAccuracyReportFilterTest {

    private static PredictionSnapshot snapshot(
            String trainNumber, String stationCode, Instant predictionMadeAt,
            HistoricalAdjustmentSource source, PredictionEvaluationStatus status) {
        return new PredictionSnapshot(
                1L, trainNumber, predictionMadeAt, stationCode,
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, source, DataProvenance.RAILRADAR, 75.0,
                status, status == PredictionEvaluationStatus.PENDING ? null : 6,
                status == PredictionEvaluationStatus.PENDING ? null : 2,
                status == PredictionEvaluationStatus.PENDING ? null : Instant.parse("2026-09-09T11:00:00Z"));
    }

    private static final PredictionSnapshot BASE = snapshot(
            "12952", "KOTA", Instant.parse("2026-09-09T10:00:00Z"),
            HistoricalAdjustmentSource.STATION_FALLBACK, PredictionEvaluationStatus.EVALUATED_EXACT);

    @Test
    void unfilteredMatchesEverything() {
        assertThat(PredictionAccuracyReportFilter.none().matches(BASE)).isTrue();
    }

    @Test
    void trainNumberMustMatchExactly() {
        PredictionAccuracyReportFilter filter =
                new PredictionAccuracyReportFilter("12952", null, null, null, null, null);
        assertThat(filter.matches(BASE)).isTrue();

        PredictionAccuracyReportFilter mismatched =
                new PredictionAccuracyReportFilter("99999", null, null, null, null, null);
        assertThat(mismatched.matches(BASE)).isFalse();
    }

    @Test
    void stationCodeMustMatchExactly() {
        PredictionAccuracyReportFilter filter =
                new PredictionAccuracyReportFilter(null, "KOTA", null, null, null, null);
        assertThat(filter.matches(BASE)).isTrue();

        PredictionAccuracyReportFilter mismatched =
                new PredictionAccuracyReportFilter(null, "RTM", null, null, null, null);
        assertThat(mismatched.matches(BASE)).isFalse();
    }

    @Test
    void timeRangeIsInclusiveOnBothEnds() {
        Instant made = BASE.predictionMadeAt();
        PredictionAccuracyReportFilter exactBounds =
                new PredictionAccuracyReportFilter(null, null, made, made, null, null);
        assertThat(exactBounds.matches(BASE)).isTrue();

        PredictionAccuracyReportFilter tooLate =
                new PredictionAccuracyReportFilter(null, null, made.plusSeconds(1), null, null, null);
        assertThat(tooLate.matches(BASE)).isFalse();

        PredictionAccuracyReportFilter tooEarly =
                new PredictionAccuracyReportFilter(null, null, null, made.minusSeconds(1), null, null);
        assertThat(tooEarly.matches(BASE)).isFalse();
    }

    @Test
    void historicalAdjustmentSourceMustMatch() {
        PredictionAccuracyReportFilter filter = new PredictionAccuracyReportFilter(
                null, null, null, null, HistoricalAdjustmentSource.STATION_FALLBACK, null);
        assertThat(filter.matches(BASE)).isTrue();

        PredictionAccuracyReportFilter mismatched = new PredictionAccuracyReportFilter(
                null, null, null, null, HistoricalAdjustmentSource.SECTION, null);
        assertThat(mismatched.matches(BASE)).isFalse();
    }

    @Test
    void evaluationStatusMustMatch() {
        PredictionAccuracyReportFilter filter = new PredictionAccuracyReportFilter(
                null, null, null, null, null, PredictionEvaluationStatus.EVALUATED_EXACT);
        assertThat(filter.matches(BASE)).isTrue();

        PredictionAccuracyReportFilter mismatched = new PredictionAccuracyReportFilter(
                null, null, null, null, null, PredictionEvaluationStatus.PENDING);
        assertThat(mismatched.matches(BASE)).isFalse();
    }

    @Test
    void allDimensionsMustMatchSimultaneously() {
        PredictionAccuracyReportFilter filter = new PredictionAccuracyReportFilter(
                "12952", "KOTA", BASE.predictionMadeAt(), BASE.predictionMadeAt(),
                HistoricalAdjustmentSource.STATION_FALLBACK, PredictionEvaluationStatus.EVALUATED_EXACT);
        assertThat(filter.matches(BASE)).isTrue();

        PredictionAccuracyReportFilter oneMismatch = new PredictionAccuracyReportFilter(
                "12952", "RTM", BASE.predictionMadeAt(), BASE.predictionMadeAt(),
                HistoricalAdjustmentSource.STATION_FALLBACK, PredictionEvaluationStatus.EVALUATED_EXACT);
        assertThat(oneMismatch.matches(BASE)).isFalse();
    }
}
