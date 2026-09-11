package com.railpredictor.model.domain;

import java.time.Instant;

/**
 * The smallest useful filter over evaluated {@link PredictionSnapshot}s for the Phase 16H-6
 * accuracy-reporting endpoint. Every field is nullable and means "don't filter on this dimension" -
 * an all-{@code null} filter matches every snapshot.
 *
 * <p>Deliberately just these six fields (train number, target station code, a
 * {@code [predictionMadeFrom, predictionMadeTo]} inclusive time range, historical-adjustment
 * source, and evaluation status) - exactly the dimensions Phase 16H-6 asked for, not every
 * conceivable filter. Cross-field validation (e.g. {@code predictionMadeFrom} not after
 * {@code predictionMadeTo}) is deliberately NOT done here - this is a plain data holder kept
 * framework/exception-free like the rest of {@code model.domain}; see
 * {@code PredictionAccuracyReportService}, which validates the filter and throws the
 * HTTP-mappable {@code InvalidAccuracyFilterException} when it's unsatisfiable.
 */
public record PredictionAccuracyReportFilter(
        String trainNumber,
        String stationCode,
        Instant predictionMadeFrom,
        Instant predictionMadeTo,
        HistoricalAdjustmentSource historicalAdjustmentSource,
        PredictionEvaluationStatus evaluationStatus) {

    /** An unfiltered filter - matches every snapshot. */
    public static PredictionAccuracyReportFilter none() {
        return new PredictionAccuracyReportFilter(null, null, null, null, null, null);
    }

    /** Whether {@code snapshot} satisfies every non-null dimension of this filter. */
    public boolean matches(PredictionSnapshot snapshot) {
        if (trainNumber != null && !trainNumber.equals(snapshot.trainNumber())) {
            return false;
        }
        if (stationCode != null && !stationCode.equals(snapshot.targetStationCode())) {
            return false;
        }
        if (predictionMadeFrom != null && snapshot.predictionMadeAt().isBefore(predictionMadeFrom)) {
            return false;
        }
        if (predictionMadeTo != null && snapshot.predictionMadeAt().isAfter(predictionMadeTo)) {
            return false;
        }
        if (historicalAdjustmentSource != null && historicalAdjustmentSource != snapshot.historicalAdjustmentSource()) {
            return false;
        }
        if (evaluationStatus != null && evaluationStatus != snapshot.evaluationStatus()) {
            return false;
        }
        return true;
    }
}
