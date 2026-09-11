package com.railpredictor.model.domain;

/**
 * The outcome of translating real operational-disruption information into a predicted delay
 * contribution (Phase 19) - deliberately four distinct states, never collapsed, so
 * {@link RailwayDisruptionAvailability#UNAVAILABLE} can never be silently read as "no disruption"
 * (see this enum's own {@link #DATA_UNAVAILABLE} vs {@link #NO_KNOWN_DISRUPTION}), and a disruption
 * that genuinely exists but can't be quantified can never be silently read as "zero impact" (see
 * {@link #PRESENT_BUT_NOT_ESTIMABLE} vs {@link #ESTIMATED}).
 *
 * <p>{@code additionalDelayMinutes} on {@code DisruptionImpact}/{@code DisruptionImpactAssessment}
 * is non-null (and may legitimately be {@code 0}) exactly for {@link #NO_KNOWN_DISRUPTION} and
 * {@link #ESTIMATED}; it is {@code null} exactly for {@link #DATA_UNAVAILABLE} and
 * {@link #PRESENT_BUT_NOT_ESTIMABLE} - the two states where the honest answer is "we don't know",
 * never a fabricated number.
 */
public enum DisruptionImpactStatus {

    /** The query was answered and nothing is currently reported - a real, positive fact
     * (contributes {@code 0} minutes, confidently, not "unknown"). */
    NO_KNOWN_DISRUPTION,

    /** No real operational-disruption data could be obtained at all - never treated as "clear". */
    DATA_UNAVAILABLE,

    /** A real disruption is reported and currently active, but this policy has no way to turn it
     * into a delay figure (missing inputs, or a type this policy doesn't yet cover) - never
     * treated as zero impact. */
    PRESENT_BUT_NOT_ESTIMABLE,

    /** A real disruption is reported, active, and this policy computed a concrete delay
     * contribution for it. */
    ESTIMATED
}
