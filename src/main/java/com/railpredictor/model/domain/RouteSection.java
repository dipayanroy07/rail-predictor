package com.railpredictor.model.domain;

/**
 * A section of track between two stations. {@code distanceKm} is null when it isn't known or
 * derivable yet - see docs/architecture.md on real vs derived vs assumed data.
 */
public record RouteSection(Station fromStation, Station toStation, Double distanceKm) {

    public RouteSection {
        fromStation = Guard.requireNonNull(fromStation, "fromStation");
        toStation = Guard.requireNonNull(toStation, "toStation");
        if (distanceKm != null) {
            Guard.requireNonNegative(distanceKm, "distanceKm");
        }
    }
}
