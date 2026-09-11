package com.railpredictor.model.domain;

/**
 * A {@link RailwayDisruption}'s temporal relationship to a reference instant (Phase 18) - computed
 * by {@code RailwayDisruptionStatusClassifier}, never stored on the disruption itself (it depends
 * on *when* you ask, not just what was reported).
 *
 * <p><b>An undated disruption is never assumed active</b> - see {@link #UNKNOWN_VALIDITY}. This
 * mirrors this codebase's standing rule against fabricating certainty the data doesn't support
 * (e.g. {@code PredictionEvaluationStatus}'s own PENDING/EVALUATED distinction).
 */
public enum RailwayDisruptionStatus {

    /** {@code effectiveFrom} is known and at-or-before the reference instant, and either
     * {@code effectiveUntil} is unknown (open-ended) or at-or-after it. */
    ACTIVE,

    /** {@code effectiveFrom} is known and strictly after the reference instant - reported, but not
     * yet in effect. */
    FUTURE_EFFECTIVE,

    /** {@code effectiveUntil} is known and strictly before the reference instant. */
    EXPIRED,

    /** {@code effectiveFrom} is not known - whether this disruption is currently active,
     * upcoming, or long over cannot be determined, so it is never treated as any of the above. */
    UNKNOWN_VALIDITY
}
