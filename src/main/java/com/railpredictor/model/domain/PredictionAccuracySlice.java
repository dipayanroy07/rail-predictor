package com.railpredictor.model.domain;

/**
 * One slice of the accuracy report (Phase 16H-6) - "overall", or one breakdown bucket (a single
 * historical-adjustment source, or a single provenance value) - always carrying both the raw
 * exact/approximate counts and the resulting {@link PredictionAccuracyComparison}, so a caller can
 * never see combined metrics without also seeing how much of the sample was only approximately
 * matched (see {@code PredictionOutcomeMatcher} on why an approximate match is a real, if smaller,
 * source of uncertainty).
 *
 * <p>{@code improvementPercent} is {@code (baselineMae - currentModelMae) / baselineMae * 100} -
 * positive means the model beats the current-delay-only baseline, negative means the baseline is
 * actually more accurate. It is {@code null}, not {@code 0.0} or {@code NaN}, whenever
 * {@code baseline.meanAbsoluteError()} is {@code 0.0}: dividing by a zero baseline error is not
 * mathematically meaningful, and a fabricated number here would misrepresent an edge case as a
 * real result.
 */
public record PredictionAccuracySlice(
        int sampleCount,
        int exactCount,
        int approximateCount,
        PredictionAccuracyComparison comparison,
        Double improvementPercent) {

    public PredictionAccuracySlice {
        Guard.requireNonNegative(sampleCount, "sampleCount");
        Guard.requireNonNegative(exactCount, "exactCount");
        Guard.requireNonNegative(approximateCount, "approximateCount");
        comparison = Guard.requireNonNull(comparison, "comparison");
        if (exactCount + approximateCount != sampleCount) {
            throw new IllegalArgumentException(
                    "exactCount + approximateCount must equal sampleCount (" + exactCount + " + "
                            + approximateCount + " != " + sampleCount + ")");
        }
    }
}
