package com.railpredictor.model.domain;

/**
 * How much of a {@link RemainingRoute} could actually be reconstructed from RailRadar's own
 * response - see {@code LiveDataRouteProvider}'s Javadoc for exactly how each value is derived.
 * Deliberately explicit rather than silently treating "couldn't reconstruct the whole thing" the
 * same as "reconstructed it completely" or "found nothing at all."
 */
public enum RouteCompleteness {

    /** Every remaining station from the current position through the actual destination was
     * identified - {@link RemainingRoute#sections()} genuinely reaches
     * {@link RemainingRoute#destinationStation()}. Includes the trivial case where the train has
     * already arrived (zero remaining sections). */
    COMPLETE,

    /** At least one remaining section beyond the current position was identified, but a station
     * somewhere between the current position and the destination could not be identified (a
     * blank/missing station code or name in RailRadar's own route data) - the walk stopped there
     * rather than skipping over the gap and guessing what lies beyond it. */
    PARTIAL,

    /** No remaining route could be established at all - either the destination itself couldn't be
     * identified, or not even one station beyond the current position could be. */
    UNAVAILABLE
}
