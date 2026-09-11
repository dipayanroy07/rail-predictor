package com.railpredictor.model.dto;

/**
 * One slice of the accuracy report - "overall", or a single source/provenance breakdown bucket
 * (Phase 16H-6). {@code exactCount}/{@code approximateCount} always sum to {@code sampleCount},
 * so combined metrics are never shown without also showing how much of the sample was only an
 * approximate journey match.
 *
 * <p>{@code improvementPercent} is {@code null} (never {@code 0.0} or a fabricated number) when
 * the baseline's mean absolute error is {@code 0.0} - "the model is N% better than the baseline"
 * is not mathematically meaningful in that case.
 */
public record AccuracySliceResponse(
        int sampleCount,
        int exactCount,
        int approximateCount,
        AccuracyComparisonResponse comparison,
        Double improvementPercent) {
}
