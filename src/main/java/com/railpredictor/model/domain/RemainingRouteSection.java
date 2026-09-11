package com.railpredictor.model.domain;

/**
 * One section of a {@link RemainingRoute} - the plain {@link RouteSection} (as
 * {@link com.railpredictor.historical.HistoricalSectionDelayProvider} already consumes) plus the
 * originating stations' own reported sequence numbers, kept only for traceability/debugging - the
 * ordering of a {@link RemainingRoute}'s section list is never derived from these, only from the
 * already-trusted array order of RailRadar's own route data (see {@link RemainingRouteStop}).
 * Either sequence may be {@code null} when RailRadar didn't report one for that station.
 */
public record RemainingRouteSection(RouteSection section, Integer fromStationSequence, Integer toStationSequence) {

    public RemainingRouteSection {
        section = Guard.requireNonNull(section, "section");
    }
}
