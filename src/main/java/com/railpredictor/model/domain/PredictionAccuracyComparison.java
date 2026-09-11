package com.railpredictor.model.domain;

/**
 * The current model's accuracy alongside a simple baseline's, computed over the same evaluated
 * {@link PredictionSnapshot}s (Phase 16H-5) - see {@code PredictionAccuracyReportBuilder}.
 *
 * <p>{@code baseline} is the "current-delay-only" prediction - i.e. what accuracy would look like
 * if the system simply assumed the train's delay never changes between the prediction moment and
 * the next station (no simulation, no historical adjustment at all). This requires no separate
 * prediction call or new infrastructure: every snapshot already stores
 * {@code currentDelayMinutes} and {@code actualDelayMinutes}, so the baseline's own error
 * ({@code currentDelayMinutes - actualDelayMinutes}) is computed directly from existing fields.
 *
 * <p>A future model variant (e.g. once weather is added) can be compared the same way - by adding
 * its own predicted-value field to the snapshot and computing its errors with the same
 * {@code PredictionAccuracyCalculator} - without changing this comparison's shape.
 */
public record PredictionAccuracyComparison(PredictionAccuracyMetrics currentModel, PredictionAccuracyMetrics baseline) {

    public PredictionAccuracyComparison {
        currentModel = Guard.requireNonNull(currentModel, "currentModel");
        baseline = Guard.requireNonNull(baseline, "baseline");
    }
}
