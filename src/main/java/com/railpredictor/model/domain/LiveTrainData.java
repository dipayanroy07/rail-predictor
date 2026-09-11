package com.railpredictor.model.domain;

import com.railpredictor.model.enums.TrainStatus;
import java.util.List;

/**
 * A snapshot of a train's live position and progress, already translated from whatever shape
 * RailRadar returns. {@code nextStation}, {@code remainingDistanceKm} and {@code speedKmh} are
 * null when RailRadar doesn't report them and they can't be derived (e.g. a terminated train has
 * no next station).
 *
 * <p>{@code destinationStation} (Phase 16H-1) is the route's actual final stop - RailRadar's own
 * route array's last entry, the same trust assumption {@code LiveTrainDataMapper} already made for
 * {@code remainingDistanceKm} before this phase - {@code null} only when even that couldn't be
 * identified. {@code remainingRouteStops} is the ordered walk from the current station onward,
 * stopping at the first station RailRadar didn't identify cleanly (see
 * {@link RemainingRouteStop}'s own Javadoc on why list order here is trustworthy) - never empty
 * when {@code destinationStation} is non-null (it always contains at least the current station
 * itself). See {@code com.railpredictor.route.RouteProvider#remainingRoute} for how these two
 * fields become a {@link RemainingRoute}.
 */
public record LiveTrainData(
        String trainNumber,
        String trainName,
        TrainStatus status,
        int currentDelayMinutes,
        Station currentStation,
        Station nextStation,
        double distanceFromOriginKm,
        Double remainingDistanceKm,
        Double speedKmh,
        Station destinationStation,
        List<RemainingRouteStop> remainingRouteStops) {

    /** Pre-Phase-16H-1 shape, preserved so existing callers need not change: no destination/
     * remaining-route information is carried. */
    public LiveTrainData(
            String trainNumber,
            String trainName,
            TrainStatus status,
            int currentDelayMinutes,
            Station currentStation,
            Station nextStation,
            double distanceFromOriginKm,
            Double remainingDistanceKm,
            Double speedKmh) {
        this(trainNumber, trainName, status, currentDelayMinutes, currentStation, nextStation,
                distanceFromOriginKm, remainingDistanceKm, speedKmh, null, List.of());
    }

    public LiveTrainData {
        trainNumber = Guard.requireNonBlank(trainNumber, "trainNumber");
        trainName = Guard.requireNonBlank(trainName, "trainName");
        status = Guard.requireNonNull(status, "status");
        Guard.requireNonNegative(currentDelayMinutes, "currentDelayMinutes");
        currentStation = Guard.requireNonNull(currentStation, "currentStation");
        Guard.requireNonNegative(distanceFromOriginKm, "distanceFromOriginKm");
        if (remainingDistanceKm != null) {
            Guard.requireNonNegative(remainingDistanceKm, "remainingDistanceKm");
        }
        if (speedKmh != null) {
            Guard.requireNonNegative(speedKmh, "speedKmh");
        }
        remainingRouteStops = List.copyOf(Guard.requireNonNull(remainingRouteStops, "remainingRouteStops"));
    }
}
