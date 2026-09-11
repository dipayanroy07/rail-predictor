package com.railpredictor.model.domain;

/**
 * One station on a train's remaining route, as reported directly by RailRadar's own {@code route}
 * array - see {@code LiveTrainDataMapper} for exactly how this is built. {@code sequence} and
 * {@code distanceFromOriginKm} are RailRadar's own reported values, carried through unchanged;
 * either may be {@code null} when RailRadar doesn't report it - never invented.
 *
 * <p>Unlike {@code HistoricalObservation}'s own {@code stationSequence} (Phase 16F), which must
 * never be trusted for ordering because persisted rows can arrive/be read back in any order,
 * a list of these carries its <em>own</em> trustworthy order: it is built by
 * {@code LiveTrainDataMapper} by walking RailRadar's single, already-ordered {@code route} array
 * for one live response - list order here already <b>is</b> the trusted physical order (the same
 * assumption {@code LiveTrainDataMapper} already relies on elsewhere, e.g. treating the array's
 * last entry as the destination). {@code sequence} is retained purely as descriptive metadata, not
 * as the sorting key.
 */
public record RemainingRouteStop(Station station, Integer sequence, Double distanceFromOriginKm) {

    public RemainingRouteStop {
        station = Guard.requireNonNull(station, "station");
        if (distanceFromOriginKm != null) {
            Guard.requireNonNegative(distanceFromOriginKm, "distanceFromOriginKm");
        }
    }
}
