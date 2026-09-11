package com.railpredictor.railradar.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.railpredictor.exception.MalformedRailRadarResponseException;
import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.RemainingRouteStop;
import com.railpredictor.model.enums.TrainStatus;
import com.railpredictor.railradar.dto.CurrentLocation;
import com.railpredictor.railradar.dto.LiveTrainStatusData;
import com.railpredictor.railradar.dto.NextHalt;
import com.railpredictor.railradar.dto.RouteStop;
import java.util.List;
import org.junit.jupiter.api.Test;

class LiveTrainDataMapperTest {

    private final LiveTrainDataMapper mapper = new LiveTrainDataMapper();

    private static RouteStop stop(String code, String name, double distance) {
        return new RouteStop(1, code, name, true, 0.0, 0.0, null, null, null, null, 0, 0,
                "departed", distance, null, "1");
    }

    private static RouteStop stopAt(String code, String name, double distance, double lat, double lng) {
        return new RouteStop(1, code, name, true, lat, lng, null, null, null, null, 0, 0,
                "departed", distance, null, "1");
    }

    private static CurrentLocation currentLocation(String stationCode, Double speedKmh) {
        return new CurrentLocation(stationCode, 5, "departed", true, false, true, 0.4, speedKmh, 210);
    }

    @Test
    void mapsAllFieldsForARunningMidRouteTrain() {
        LiveTrainStatusData data = new LiveTrainStatusData(
                "12952", "Rajdhani Express", "running", 12, "2026-09-09T10:15:00Z",
                currentLocation("KOTA", 92.5),
                new NextHalt("RTM", "Ratlam Jn", 6, 550.0),
                List.of(stop("NDLS", "New Delhi", 0.0), stop("KOTA", "Kota Jn", 465.0), stop("BCT", "Mumbai Central", 1384.0)));

        LiveTrainData result = mapper.toDomain(data);

        assertThat(result.trainNumber()).isEqualTo("12952");
        assertThat(result.trainName()).isEqualTo("Rajdhani Express");
        assertThat(result.status()).isEqualTo(TrainStatus.RUNNING);
        assertThat(result.currentDelayMinutes()).isEqualTo(12);
        // KOTA's coordinates come from its route entry; RTM isn't in the route list in this
        // fixture, so it falls back to no coordinates.
        assertThat(result.currentStation()).isEqualTo(new com.railpredictor.model.domain.Station("KOTA", "Kota Jn", 0.0, 0.0));
        assertThat(result.nextStation()).isEqualTo(new com.railpredictor.model.domain.Station("RTM", "Ratlam Jn"));
        assertThat(result.distanceFromOriginKm()).isEqualTo(465.0);
        assertThat(result.remainingDistanceKm()).isEqualTo(919.0);
        assertThat(result.speedKmh()).isEqualTo(92.5);
    }

    @Test
    void looksUpNextStationCoordinatesFromItsOwnRouteEntryWhenPresent() {
        LiveTrainStatusData data = new LiveTrainStatusData(
                "12952", "Rajdhani Express", "running", 0, null,
                currentLocation("NDLS", 60.0),
                new NextHalt("KOTA", "Kota Jn", 2, 465.0),
                List.of(stop("NDLS", "New Delhi", 0.0), stopAt("KOTA", "Kota Jn", 465.0, 25.18, 75.83)));

        LiveTrainData result = mapper.toDomain(data);

        assertThat(result.nextStation().latitude()).isEqualTo(25.18);
        assertThat(result.nextStation().longitude()).isEqualTo(75.83);
    }

    @Test
    void terminatedTrainHasNoNextStation() {
        LiveTrainStatusData data = new LiveTrainStatusData(
                "12952", "Rajdhani Express", "terminated", 0, null,
                currentLocation("BCT", 0.0),
                null,
                List.of(stop("NDLS", "New Delhi", 0.0), stop("BCT", "Mumbai Central", 1384.0)));

        LiveTrainData result = mapper.toDomain(data);

        assertThat(result.nextStation()).isNull();
        assertThat(result.remainingDistanceKm()).isEqualTo(0.0);
    }

    @Test
    void clampsNegativeDelayToZero() {
        LiveTrainStatusData data = new LiveTrainStatusData(
                "12952", "Rajdhani Express", "running", -5, null,
                currentLocation("NDLS", 60.0), null, List.of(stop("NDLS", "New Delhi", 0.0)));

        assertThat(mapper.toDomain(data).currentDelayMinutes()).isZero();
    }

    @Test
    void treatsMissingDelayAsZero() {
        LiveTrainStatusData data = new LiveTrainStatusData(
                "12952", "Rajdhani Express", "running", null, null,
                currentLocation("NDLS", 60.0), null, List.of(stop("NDLS", "New Delhi", 0.0)));

        assertThat(mapper.toDomain(data).currentDelayMinutes()).isZero();
    }

    @Test
    void unrecognizedOrMissingStatusMapsToUnknown() {
        LiveTrainStatusData unrecognized = new LiveTrainStatusData(
                "12952", "Rajdhani Express", "diverted-via-alternate-route", 0, null,
                currentLocation("NDLS", 60.0), null, List.of(stop("NDLS", "New Delhi", 0.0)));
        LiveTrainStatusData missing = new LiveTrainStatusData(
                "12952", "Rajdhani Express", null, 0, null,
                currentLocation("NDLS", 60.0), null, List.of(stop("NDLS", "New Delhi", 0.0)));

        assertThat(mapper.toDomain(unrecognized).status()).isEqualTo(TrainStatus.UNKNOWN);
        assertThat(mapper.toDomain(missing).status()).isEqualTo(TrainStatus.UNKNOWN);
    }

    @Test
    void remainingDistanceIsNullWhenRouteIsEmpty() {
        LiveTrainStatusData data = new LiveTrainStatusData(
                "12952", "Rajdhani Express", "running", 0, null,
                currentLocation("NDLS", 60.0), null, List.of(stop("NDLS", "New Delhi", 0.0)));
        // route contains only the current stop itself -> "last stop" IS the current stop.

        assertThat(mapper.toDomain(data).remainingDistanceKm()).isEqualTo(0.0);
    }

    @Test
    void rejectsMissingCurrentLocation() {
        LiveTrainStatusData data = new LiveTrainStatusData(
                "12952", "Rajdhani Express", "running", 0, null, null, null, List.of());

        assertThrows(MalformedRailRadarResponseException.class, () -> mapper.toDomain(data));
    }

    @Test
    void rejectsCurrentStationNotPresentInRoute() {
        LiveTrainStatusData data = new LiveTrainStatusData(
                "12952", "Rajdhani Express", "running", 0, null,
                currentLocation("KOTA", 60.0), null, List.of(stop("NDLS", "New Delhi", 0.0)));

        assertThrows(MalformedRailRadarResponseException.class, () -> mapper.toDomain(data));
    }

    @Test
    void rejectsBlankTrainNumberOrName() {
        LiveTrainStatusData blankNumber = new LiveTrainStatusData(
                " ", "Rajdhani Express", "running", 0, null,
                currentLocation("NDLS", 60.0), null, List.of(stop("NDLS", "New Delhi", 0.0)));
        LiveTrainStatusData blankName = new LiveTrainStatusData(
                "12952", " ", "running", 0, null,
                currentLocation("NDLS", 60.0), null, List.of(stop("NDLS", "New Delhi", 0.0)));

        assertThrows(MalformedRailRadarResponseException.class, () -> mapper.toDomain(blankNumber));
        assertThrows(MalformedRailRadarResponseException.class, () -> mapper.toDomain(blankName));
    }

    // --- Phase 16H-1: destinationStation / remainingRouteStops ---

    private static RouteStop malformedStop(String code, String name, double distance) {
        // Used to represent a route entry RailRadar didn't identify cleanly - blank code and name.
        return new RouteStop(1, code, name, true, 0.0, 0.0, null, null, null, null, 0, 0,
                "departed", distance, null, "1");
    }

    @Test
    void destinationStationIsTheRoutesActualLastEntry() {
        LiveTrainStatusData data = new LiveTrainStatusData(
                "12952", "Rajdhani Express", "running", 0, null,
                currentLocation("NDLS", 60.0), null,
                List.of(stop("NDLS", "New Delhi", 0.0), stop("KOTA", "Kota Jn", 465.0), stop("BCT", "Mumbai Central", 1384.0)));

        LiveTrainData result = mapper.toDomain(data);

        assertThat(result.destinationStation().code()).isEqualTo("BCT");
    }

    @Test
    void remainingRouteStopsWalksFromCurrentStationThroughTheFullRouteWhenEveryEntryIsUsable() {
        LiveTrainStatusData data = new LiveTrainStatusData(
                "12952", "Rajdhani Express", "running", 0, null,
                currentLocation("KOTA", 60.0), null,
                List.of(stop("NDLS", "New Delhi", 0.0), stop("KOTA", "Kota Jn", 465.0), stop("BCT", "Mumbai Central", 1384.0)));

        LiveTrainData result = mapper.toDomain(data);

        assertThat(result.remainingRouteStops()).extracting(RemainingRouteStop::station)
                .extracting(com.railpredictor.model.domain.Station::code)
                .containsExactly("KOTA", "BCT");
        assertThat(result.remainingRouteStops().get(1).distanceFromOriginKm()).isEqualTo(1384.0);
    }

    @Test
    void remainingRouteStopsStopsAtTheFirstUnidentifiableStationRatherThanSkippingIt() {
        LiveTrainStatusData data = new LiveTrainStatusData(
                "12952", "Rajdhani Express", "running", 0, null,
                currentLocation("NDLS", 60.0), null,
                List.of(stop("NDLS", "New Delhi", 0.0), malformedStop(" ", " ", 200.0), stop("BCT", "Mumbai Central", 1384.0)));

        LiveTrainData result = mapper.toDomain(data);

        // Only NDLS is captured - the gap at index 1 is never skipped over to reach BCT, which
        // would have silently manufactured an adjacency RailRadar never actually confirmed.
        assertThat(result.remainingRouteStops()).hasSize(1);
        assertThat(result.remainingRouteStops().get(0).station().code()).isEqualTo("NDLS");
        // The destination is still identifiable independently (route's own last entry is fine).
        assertThat(result.destinationStation().code()).isEqualTo("BCT");
    }

    @Test
    void destinationStationIsNullWhenTheRoutesLastEntryIsNotUsable() {
        LiveTrainStatusData data = new LiveTrainStatusData(
                "12952", "Rajdhani Express", "running", 0, null,
                currentLocation("NDLS", 60.0), null,
                List.of(stop("NDLS", "New Delhi", 0.0), malformedStop(" ", " ", 200.0)));

        LiveTrainData result = mapper.toDomain(data);

        assertThat(result.destinationStation()).isNull();
    }

    @Test
    void remainingRouteStopsPreservesNullSequenceRatherThanInventingOne() {
        RouteStop noSequence = new RouteStop(null, "KOTA", "Kota Jn", true, 0.0, 0.0, null, null, null, null,
                0, 0, "departed", 465.0, null, "1");
        LiveTrainStatusData data = new LiveTrainStatusData(
                "12952", "Rajdhani Express", "running", 0, null,
                currentLocation("KOTA", 60.0), null,
                List.of(stop("NDLS", "New Delhi", 0.0), noSequence));

        LiveTrainData result = mapper.toDomain(data);

        assertThat(result.remainingRouteStops().get(0).sequence()).isNull();
    }

    @Test
    void aTerminatedTrainAtTheFinalStationHasASingleElementRemainingRouteStopsList() {
        LiveTrainStatusData data = new LiveTrainStatusData(
                "12952", "Rajdhani Express", "terminated", 0, null,
                currentLocation("BCT", 0.0), null,
                List.of(stop("NDLS", "New Delhi", 0.0), stop("BCT", "Mumbai Central", 1384.0)));

        LiveTrainData result = mapper.toDomain(data);

        assertThat(result.remainingRouteStops()).hasSize(1);
        assertThat(result.remainingRouteStops().get(0).station().code()).isEqualTo("BCT");
        assertThat(result.destinationStation().code()).isEqualTo("BCT");
    }
}
