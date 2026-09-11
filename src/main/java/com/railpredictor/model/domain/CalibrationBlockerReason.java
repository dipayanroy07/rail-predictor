package com.railpredictor.model.domain;

/**
 * Why a {@link CalibrationAssessment} could not proceed past
 * {@link CalibrationStatus#INSUFFICIENT_DATA} (Phase 20) - distinguishing a data-volume problem
 * (which more real evaluation traffic would eventually fix) from a structural one (which no amount
 * of additional data can fix without a separate, deliberate design change).
 */
public enum CalibrationBlockerReason {

    /** Nothing is blocking calibration - a real assessment was produced. */
    NONE,

    /** Fewer real evaluated samples exist than
     * {@code evaluation.calibration.minimum-sample-count} requires. More real evaluation traffic
     * would eventually resolve this. */
    INSUFFICIENT_SAMPLE_SIZE,

    /** The parameter being assessed does not influence the currently-evaluated quantity
     * ({@code PredictionSnapshot.predictedNextStationDelayMinutes}) at all, by the existing
     * architecture's own deliberate design - see docs/prediction-model.md's Phase 20 notes (e.g.
     * {@code historicalAdjustmentMinutes}/{@code disruptionImpactMinutes} only affect the
     * destination-scoped {@code predictedTotalDelayMinutes}, which this system does not evaluate).
     * No amount of additional data resolves this - it requires a deliberate, separate scope
     * decision (evaluating destination-level accuracy, or changing what the next-station target
     * includes), neither of which this phase performs. */
    METRIC_STRUCTURALLY_UNAFFECTED,

    /** Every contributing sample is mock-derived, not a real observation - mock data must never be
     * treated as empirical calibration evidence, regardless of how much of it exists. */
    MOCK_DATA_ONLY,

    /** The candidate training and validation periods overlap in time - a calibration methodology
     * bug, never allowed to produce a result. */
    TRAINING_VALIDATION_OVERLAP
}
