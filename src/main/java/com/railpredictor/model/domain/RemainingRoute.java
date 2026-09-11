package com.railpredictor.model.domain;

import java.util.List;

/**
 * The remaining sections of a train's journey from its current position to its destination, as
 * far as they can actually be reconstructed from RailRadar's own route data - see
 * {@code LiveDataRouteProvider} for exactly how. Never fabricates an intermediate station or a
 * distance: see {@link RouteCompleteness} for what to do when the reconstruction is incomplete.
 *
 * <p>{@code destinationStation} is {@code null} only when {@code completeness} is
 * {@link RouteCompleteness#UNAVAILABLE} <em>and</em> not even the destination itself could be
 * identified - {@code sections} is always empty in that case too. Otherwise (whether
 * {@code COMPLETE} or {@code PARTIAL}) the destination is known even if not every section leading
 * to it could be.
 */
public record RemainingRoute(
        Station currentStation,
        Station destinationStation,
        List<RemainingRouteSection> sections,
        RouteCompleteness completeness) {

    public RemainingRoute {
        currentStation = Guard.requireNonNull(currentStation, "currentStation");
        sections = List.copyOf(Guard.requireNonNull(sections, "sections"));
        completeness = Guard.requireNonNull(completeness, "completeness");
        if (completeness == RouteCompleteness.UNAVAILABLE && (destinationStation != null || !sections.isEmpty())) {
            throw new IllegalArgumentException(
                    "destinationStation and sections must both be empty/null when completeness is UNAVAILABLE");
        }
        if (completeness != RouteCompleteness.UNAVAILABLE && destinationStation == null) {
            throw new IllegalArgumentException("destinationStation must be known unless completeness is UNAVAILABLE");
        }
    }
}
