package com.railpredictor.model.domain;

/**
 * Whether a numeric assumption in this codebase (a {@code DisruptionImpactPolicy} default, Phase
 * 19; a historical-adjustment weight or confidence formula, Phase 20) is backed by real evaluated
 * evidence - mirrors {@code PredictionEvaluationMode}'s exact "some values always produced today,
 * others reserved for a future capability" precedent.
 *
 * <p>This is a statement about a <em>policy or parameter</em>, not about any one prediction - every
 * heuristic default in {@code DisruptionImpactProperties} is a provisional engineering assumption,
 * not a number derived from measuring real disruption-to-delay outcomes (no such measurements
 * exist yet in this codebase - see docs/prediction-model.md's Phase 19/20 notes on why).
 *
 * <p><b>Ordering/severity, loosely {@code NOT_CALIBRATED} &lt; {@code INSUFFICIENT_DATA} &lt;
 * {@code PROVISIONALLY_CALIBRATED} &lt; {@code VALIDATED}</b> (Phase 20) - though nothing in this
 * codebase currently orders or compares these programmatically; the enum exists to make the
 * distinction explicit and machine-readable, never to be inferred from passing tests alone (unit
 * tests prove code correctness, never that a real-world numeric assumption is accurate).
 */
public enum CalibrationStatus {

    /** No calibration attempt has been made at all for this parameter - the number is a bare
     * engineering default, never evaluated against any evidence, real or synthetic. */
    NOT_CALIBRATED,

    /** A calibration attempt was made, but real evidence was insufficient (too few evaluated
     * samples, or the evaluated metric structurally cannot reflect this parameter at all - see
     * {@code CalibrationBlockerReason}) - {@code HeuristicDisruptionImpactPolicy} always reports
     * this today, since this codebase has zero real evaluated disruption-to-delay outcomes. */
    INSUFFICIENT_DATA,

    /** A candidate value was estimated from a real training-period sample and checked against a
     * later, disjoint validation-period sample, but the sample sizes and/or observed improvement
     * are too small to treat as more than a provisional signal - not yet a confident
     * recommendation to change a production default. */
    PROVISIONALLY_CALIBRATED,

    /** A calibrated value has been validated against a real, sufficiently large, disjoint
     * validation-period sample with a practically meaningful and statistically detectable
     * improvement. Reserved for a future phase - no code in this application produces this value
     * yet; see docs/prediction-model.md's Phase 19/20 "calibration foundation" notes for what
     * would be needed first. Never produced merely because unit tests pass. */
    VALIDATED
}
