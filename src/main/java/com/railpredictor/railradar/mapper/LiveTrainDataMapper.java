package com.railpredictor.railradar.mapper;

import com.railpredictor.exception.MalformedRailRadarResponseException;
import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.RemainingRouteStop;
import com.railpredictor.model.domain.Station;
import com.railpredictor.model.enums.TrainStatus;
import com.railpredictor.railradar.dto.CurrentLocation;
import com.railpredictor.railradar.dto.LiveTrainStatusData;
import com.railpredictor.railradar.dto.NextHalt;
import com.railpredictor.railradar.dto.RouteStop;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Translates RailRadar's raw {@link LiveTrainStatusData} into the internal, framework-free
 * {@link LiveTrainData}. RailRadar DTOs must never be visible outside this package's mapping
 * step - see docs/architecture.md.
 */
@Component
public class LiveTrainDataMapper {

    public LiveTrainData toDomain(LiveTrainStatusData data) {
        String trainNumber = requireField(data.trainNumber(), "trainNumber");
        String trainName = requireField(data.trainName(), "trainName");

        CurrentLocation currentLocation = data.currentLocation();
        if (currentLocation == null || currentLocation.stationCode() == null) {
            throw new MalformedRailRadarResponseException(
                    "RailRadar response for train " + trainNumber + " has no currentLocation.stationCode");
        }

        RouteStop currentStop = findStop(data.route(), currentLocation.stationCode())
                .orElseThrow(() -> new MalformedRailRadarResponseException(
                        "RailRadar response for train " + trainNumber
                                + " has currentLocation.stationCode '" + currentLocation.stationCode()
                                + "' that isn't in its own route"));
        if (currentStop.distance() == null) {
            throw new MalformedRailRadarResponseException(
                    "RailRadar route entry for station '" + currentLocation.stationCode()
                            + "' on train " + trainNumber + " has no distance");
        }

        Station currentStation = new Station(
                currentLocation.stationCode(), currentStop.stationName(), currentStop.lat(), currentStop.lng());
        double distanceFromOriginKm = currentStop.distance();

        NextHalt nextHalt = data.nextHalt();
        Station nextStation = nextHalt == null ? null : buildNextStation(nextHalt, data.route());

        Double remainingDistanceKm = remainingDistance(data.route(), distanceFromOriginKm);

        int delayMinutes = data.delayMinutes() == null ? 0 : data.delayMinutes();
        if (delayMinutes < 0) {
            // RailRadar can report a negative delay for a train running ahead of schedule, but the
            // domain model treats "current delay" as non-negative - clamp early running to zero.
            delayMinutes = 0;
        }

        Station destinationStation = destinationStation(data.route());
        List<RemainingRouteStop> remainingRouteStops = buildRemainingRouteStops(data.route(), currentLocation.stationCode());

        return new LiveTrainData(
                trainNumber,
                trainName,
                mapStatus(data.status()),
                delayMinutes,
                currentStation,
                nextStation,
                distanceFromOriginKm,
                remainingDistanceKm,
                currentLocation.speedKmh(),
                destinationStation,
                remainingRouteStops);
    }

    /**
     * The route's actual final stop - the same "last entry in {@code route[]} is the destination"
     * trust assumption {@link #remainingDistance} already made before Phase 16H-1, reused here for
     * a second purpose. {@code null} when the route is missing/empty or that last entry's own
     * station code/name isn't usable - never fabricated.
     */
    private static Station destinationStation(List<RouteStop> route) {
        if (route == null || route.isEmpty()) {
            return null;
        }
        RouteStop last = route.get(route.size() - 1);
        if (!hasUsableIdentity(last)) {
            return null;
        }
        return new Station(last.stationCode(), last.stationName(), last.lat(), last.lng());
    }

    /**
     * Walks {@code route[]} from the current station (inclusive) onward, in the array's own
     * already-trusted order (see {@link RemainingRouteStop}'s Javadoc), stopping at the first stop
     * whose station code or name isn't usable - never skipped over and reconnected past, which
     * would silently manufacture an adjacency RailRadar never actually confirmed. Always contains
     * at least the current station itself, since {@code currentLocation.stationCode()} is already
     * required to match a usable route entry before this method is ever reached (see the
     * {@code MalformedRailRadarResponseException} checks above).
     */
    private static List<RemainingRouteStop> buildRemainingRouteStops(List<RouteStop> route, String currentStationCode) {
        int currentIndex = -1;
        for (int i = 0; i < route.size(); i++) {
            if (currentStationCode.equals(route.get(i).stationCode())) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex < 0) {
            return List.of();
        }
        List<RemainingRouteStop> stops = new ArrayList<>();
        for (int i = currentIndex; i < route.size(); i++) {
            RouteStop stop = route.get(i);
            if (!hasUsableIdentity(stop)) {
                break;
            }
            Station station = new Station(stop.stationCode(), stop.stationName(), stop.lat(), stop.lng());
            stops.add(new RemainingRouteStop(station, stop.sequence(), stop.distance()));
        }
        return stops;
    }

    private static boolean hasUsableIdentity(RouteStop stop) {
        return stop.stationCode() != null && !stop.stationCode().isBlank()
                && stop.stationName() != null && !stop.stationName().isBlank();
    }

    private static Station buildNextStation(NextHalt nextHalt, List<RouteStop> route) {
        // NextHalt itself carries no coordinates; look them up from the matching route entry if
        // present. A missing match isn't an error - fall back to a station with no coordinates.
        return findStop(route, nextHalt.stationCode())
                .map(stop -> new Station(nextHalt.stationCode(), nextHalt.stationName(), stop.lat(), stop.lng()))
                .orElseGet(() -> new Station(nextHalt.stationCode(), nextHalt.stationName()));
    }

    private static java.util.Optional<RouteStop> findStop(List<RouteStop> route, String stationCode) {
        if (route == null) {
            return java.util.Optional.empty();
        }
        return route.stream().filter(stop -> stationCode.equals(stop.stationCode())).findFirst();
    }

    private static Double remainingDistance(List<RouteStop> route, double distanceFromOriginKm) {
        if (route == null || route.isEmpty()) {
            return null;
        }
        RouteStop destination = route.get(route.size() - 1);
        if (destination.distance() == null) {
            return null;
        }
        // Clamp: a train reported at its final station can momentarily show a current distance
        // equal to (or, with slightly stale data, just past) the total route distance.
        return Math.max(0.0, destination.distance() - distanceFromOriginKm);
    }

    private static TrainStatus mapStatus(String rawStatus) {
        if (rawStatus == null) {
            return TrainStatus.UNKNOWN;
        }
        return switch (rawStatus.trim().toLowerCase(Locale.ROOT)) {
            case "running" -> TrainStatus.RUNNING;
            case "scheduled" -> TrainStatus.SCHEDULED;
            case "delayed" -> TrainStatus.DELAYED;
            case "terminated" -> TrainStatus.TERMINATED;
            case "cancelled", "canceled" -> TrainStatus.CANCELLED;
            default -> TrainStatus.UNKNOWN;
        };
    }

    private static String requireField(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new MalformedRailRadarResponseException("RailRadar response is missing required field '" + fieldName + "'");
        }
        return value;
    }
}
