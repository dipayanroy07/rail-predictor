package com.railpredictor.model.domain;

/**
 * How much of a {@link RemainingRouteHistoricalSummary} is actually backed by usable section
 * history - orthogonal to {@link RouteCompleteness} (which describes the route topology itself,
 * not whether historical data exists for it). Deliberately distinct from a single "no data"
 * boolean, per Phase 16H-1's own instruction not to collapse the various failure modes into one
 * ambiguous signal.
 */
public enum RemainingRouteHistoricalStatus {

    /** The remaining route had zero sections to look up in the first place (the train has already
     * arrived, or the route itself is {@link RouteCompleteness#UNAVAILABLE}) - there was nothing
     * to query. */
    NO_REMAINING_SECTIONS,

    /** Every remaining section returned {@code AVAILABLE} historical data. */
    ALL_SECTIONS_AVAILABLE,

    /** At least one remaining section returned {@code AVAILABLE} data, and at least one did not. */
    PARTIAL_SECTIONS_AVAILABLE,

    /** Remaining sections exist, but none of them returned {@code AVAILABLE} historical data
     * (each was {@code INSUFFICIENT_SAMPLES} or {@code NOT_FOUND}). */
    NO_SECTIONS_AVAILABLE
}
