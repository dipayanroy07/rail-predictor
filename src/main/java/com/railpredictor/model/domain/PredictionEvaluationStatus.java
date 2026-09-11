package com.railpredictor.model.domain;

/**
 * Whether/how a {@link PredictionSnapshot} has been checked against a real, later-observed
 * outcome (Phase 16H-5). Deliberately distinguishes "not yet observed" from "observed, but the
 * match isn't fully certain" from "structurally impossible to evaluate" - collapsing these into
 * one boolean would hide exactly the journey-identity uncertainty this system must stay honest
 * about (see docs/historical-data-design.md's Phase 16E notes on why {@code journeyDate} is not a
 * verified physical-journey identifier).
 */
public enum PredictionEvaluationStatus {

    /** No matching future observation has been found yet - the train presumably hasn't reached
     * the target station yet. Re-checked on a later evaluation pass. */
    PENDING,

    /** Exactly one candidate observation was found for (trainNumber, targetStationCode) recorded
     * strictly after the prediction was made - an unambiguous match. */
    EVALUATED_EXACT,

    /** More than one candidate observation was found (e.g. the same train service ran again
     * before this snapshot was evaluated) - the earliest one was used as the best available
     * guess, but which physical journey it truly belongs to is not certain. */
    EVALUATED_APPROXIMATE,

    /** This snapshot cannot be evaluated at all - e.g. no target station was known at prediction
     * time. Never assigned merely because no observation has appeared yet (that's
     * {@link #PENDING}). */
    NOT_EVALUABLE
}
