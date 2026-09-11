package com.railpredictor.model.domain;

import com.railpredictor.model.enums.ConfidenceLevel;

/**
 * One confidence bucket's real-world outcome (Phase 20) - "of the predictions this system said
 * were {@code level} confidence, how large was the actual error". {@code sufficientSample}
 * gates whether {@code meanAbsoluteError}/{@code errorDistribution} should be trusted at all - see
 * {@code ConfidenceCalibrationAuditor}'s own minimum-sample-count gate.
 */
public record ConfidenceBucketMetrics(
        ConfidenceLevel level,
        int sampleCount,
        boolean sufficientSample,
        double meanAbsoluteError,
        ErrorDistribution errorDistribution) {

    public ConfidenceBucketMetrics {
        level = Guard.requireNonNull(level, "level");
        Guard.requireNonNegative(sampleCount, "sampleCount");
        Guard.requireNonNegative(meanAbsoluteError, "meanAbsoluteError");
        errorDistribution = Guard.requireNonNull(errorDistribution, "errorDistribution");
    }
}
