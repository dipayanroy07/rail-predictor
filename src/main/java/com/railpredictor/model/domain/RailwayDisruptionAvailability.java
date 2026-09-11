package com.railpredictor.model.domain;

/**
 * Whether a {@link RailwayDisruptionProvider}-style query could actually be answered (Phase 18) -
 * mirrors {@code SectionHistoricalDelayStatus}'s "was the query even answerable" role, but for a
 * different reason: this distinguishes a real "nothing is currently reported" fact from "we don't
 * know", which {@code SectionHistoricalDelayStatus} does not need to (a historical-statistics query
 * is either answerable or not, there is no "confirmed there is no history").
 *
 * <p><b>Never conflate these two states</b> - the entire reason this enum exists:
 * <ul>
 *   <li>{@link #AVAILABLE} with an empty disruption list means "queried successfully; nothing is
 *       currently reported" (NO_KNOWN_DISRUPTION) - a real, positive fact.</li>
 *   <li>{@link #UNAVAILABLE} means "could not determine" (DISRUPTION_DATA_UNAVAILABLE) - the
 *       absence of data, never to be read as "the route is clear".</li>
 * </ul>
 */
public enum RailwayDisruptionAvailability {

    /** The query was answered - {@code disruptions} reflects everything currently known (possibly
     * empty, meaning none are currently reported). */
    AVAILABLE,

    /** No real operational-disruption data could be obtained (no provider configured, or a query
     * failure) - {@code disruptions} is always empty here, but that emptiness must never be
     * interpreted as "no disruptions exist". */
    UNAVAILABLE
}
