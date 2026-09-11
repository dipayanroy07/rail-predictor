package com.railpredictor.model.domain;

/**
 * A small set of distributional statistics over a set of signed prediction errors (Phase 20),
 * beyond the mean-based {@link PredictionAccuracyMetrics} (MAE/RMSE/bias) - deliberately minimal
 * (median and P90 of the *absolute* error only), not a full histogram or bootstrap interval, per
 * this phase's own instruction not to introduce sophisticated statistics merely for appearance.
 *
 * <p>{@code sampleCount == 0} produces {@code 0.0} for both statistics - a placeholder, matching
 * {@link PredictionAccuracyMetrics}'s own empty-data convention, never a fabricated measurement.
 */
public record ErrorDistribution(int sampleCount, double medianAbsoluteError, double p90AbsoluteError) {

    public ErrorDistribution {
        Guard.requireNonNegative(sampleCount, "sampleCount");
        Guard.requireNonNegative(medianAbsoluteError, "medianAbsoluteError");
        Guard.requireNonNegative(p90AbsoluteError, "p90AbsoluteError");
    }
}
