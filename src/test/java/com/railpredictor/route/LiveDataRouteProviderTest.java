package com.railpredictor.route;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.RemainingRoute;
import com.railpredictor.model.domain.RemainingRouteStop;
import com.railpredictor.model.domain.RouteCompleteness;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.Station;
import com.railpredictor.model.enums.TrainStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LiveDataRouteProviderTest {

    private final LiveDataRouteProvider provider = new LiveDataRouteProvider();

    @Test
    void buildsSectionFromCurrentAndNextStationWithUnknownDistance() {
        Station ndls = new Station("NDLS", "New Delhi");
        Station gzb = new Station("GZB", "Ghaziabad");
        LiveTrainData train = new LiveTrainData(
                "12345", "Test Express", TrainStatus.RUNNING, 5, ndls, gzb, 10.0, 25.0, 80.0);

        Optional<RouteSection> section = provider.currentSection(train);

        assertThat(section).contains(new RouteSection(ndls, gzb, null));
    }

    @Test
    void isEmptyWhenTrainHasNoNextStation() {
        Station bct = new Station("BCT", "Mumbai Central");
        LiveTrainData terminated = new LiveTrainData(
                "12345", "Test Express", TrainStatus.TERMINATED, 0, bct, null, 1384.0, null, null);

        assertThat(provider.currentSection(terminated)).isEmpty();
    }

    // --- Phase 16H-1: remainingRoute() ---

    private static Station station(String code) {
        return new Station(code, code + " Station");
    }

    private static LiveTrainData trainWithRemainingRoute(
            Station current, Station destination, List<RemainingRouteStop> remainingRouteStops) {
        return new LiveTrainData(
                "12345", "Test Express", TrainStatus.RUNNING, 5, current, null,
                10.0, null, null, destination, remainingRouteStops);
    }

    @Test
    void oneRemainingSectionIsComplete() {
        LiveTrainData train = trainWithRemainingRoute(station("A"), station("B"), List.of(
                new RemainingRouteStop(station("A"), 1, 0.0),
                new RemainingRouteStop(station("B"), 2, 100.0)));

        RemainingRoute route = provider.remainingRoute(train);

        assertThat(route.completeness()).isEqualTo(RouteCompleteness.COMPLETE);
        assertThat(route.sections()).hasSize(1);
        assertThat(route.sections().get(0).section()).isEqualTo(new RouteSection(station("A"), station("B"), 100.0));
        assertThat(route.destinationStation()).isEqualTo(station("B"));
    }

    @Test
    void multipleRemainingSectionsAreComplete() {
        LiveTrainData train = trainWithRemainingRoute(station("A"), station("D"), List.of(
                new RemainingRouteStop(station("A"), 1, 0.0),
                new RemainingRouteStop(station("B"), 2, 50.0),
                new RemainingRouteStop(station("C"), 3, 120.0),
                new RemainingRouteStop(station("D"), 4, 200.0)));

        RemainingRoute route = provider.remainingRoute(train);

        assertThat(route.completeness()).isEqualTo(RouteCompleteness.COMPLETE);
        assertThat(route.sections()).hasSize(3);
        assertThat(route.sections().get(1).section().fromStation().code()).isEqualTo("B");
        assertThat(route.sections().get(1).section().toStation().code()).isEqualTo("C");
        assertThat(route.sections().get(1).section().distanceKm()).isEqualTo(70.0);
    }

    @Test
    void currentStationAlreadyAtDestinationProducesZeroSectionsButStillComplete() {
        LiveTrainData train = trainWithRemainingRoute(station("D"), station("D"), List.of(
                new RemainingRouteStop(station("D"), 1, 500.0)));

        RemainingRoute route = provider.remainingRoute(train);

        assertThat(route.completeness()).isEqualTo(RouteCompleteness.COMPLETE);
        assertThat(route.sections()).isEmpty();
        assertThat(route.destinationStation()).isEqualTo(station("D"));
    }

    @Test
    void anIncompleteRouteThatDoesNotReachTheDestinationIsPartial() {
        LiveTrainData train = trainWithRemainingRoute(station("A"), station("D"), List.of(
                new RemainingRouteStop(station("A"), 1, 0.0),
                new RemainingRouteStop(station("B"), 2, 50.0)));
        // "D" is the known destination but the walk never reached it (a gap occurred beyond B).

        RemainingRoute route = provider.remainingRoute(train);

        assertThat(route.completeness()).isEqualTo(RouteCompleteness.PARTIAL);
        assertThat(route.sections()).hasSize(1);
        assertThat(route.destinationStation()).isEqualTo(station("D"));
    }

    @Test
    void missingSequenceIsPreservedNotFabricated() {
        LiveTrainData train = trainWithRemainingRoute(station("A"), station("B"), List.of(
                new RemainingRouteStop(station("A"), null, 0.0),
                new RemainingRouteStop(station("B"), 2, 100.0)));

        RemainingRoute route = provider.remainingRoute(train);

        assertThat(route.sections().get(0).fromStationSequence()).isNull();
        assertThat(route.sections().get(0).toStationSequence()).isEqualTo(2);
    }

    @Test
    void missingDistanceOnEitherEndpointLeavesTheSectionsDistanceNull() {
        LiveTrainData train = trainWithRemainingRoute(station("A"), station("B"), List.of(
                new RemainingRouteStop(station("A"), 1, null),
                new RemainingRouteStop(station("B"), 2, 100.0)));

        RemainingRoute route = provider.remainingRoute(train);

        assertThat(route.sections().get(0).section().distanceKm()).isNull();
    }

    @Test
    void noUsableTopologyIsUnavailableWhenTheDestinationItselfIsUnknown() {
        LiveTrainData train = trainWithRemainingRoute(station("A"), null, List.of(
                new RemainingRouteStop(station("A"), 1, 0.0)));

        RemainingRoute route = provider.remainingRoute(train);

        assertThat(route.completeness()).isEqualTo(RouteCompleteness.UNAVAILABLE);
        assertThat(route.sections()).isEmpty();
        assertThat(route.destinationStation()).isNull();
    }

    @Test
    void noUsableTopologyIsUnavailableWhenNotEvenOneStepBeyondCurrentIsKnown() {
        // Destination is known, but the walk only ever captured the current station itself and
        // the current station is NOT the destination - not even one real step could be confirmed.
        LiveTrainData train = trainWithRemainingRoute(station("A"), station("D"), List.of(
                new RemainingRouteStop(station("A"), 1, 0.0)));

        RemainingRoute route = provider.remainingRoute(train);

        assertThat(route.completeness()).isEqualTo(RouteCompleteness.UNAVAILABLE);
        assertThat(route.sections()).isEmpty();
    }

    @Test
    void noSectionIsEverFabricatedAcrossAGap() {
        // Only two stations captured (A and C is never in the list - a gap was hit at B), so the
        // only section built is A->C is never asserted here; instead we verify no phantom
        // three-station chain appears when only a two-stop list was actually provided.
        LiveTrainData train = trainWithRemainingRoute(station("A"), station("D"), List.of(
                new RemainingRouteStop(station("A"), 1, 0.0),
                new RemainingRouteStop(station("C"), 3, 120.0)));

        RemainingRoute route = provider.remainingRoute(train);

        assertThat(route.sections()).hasSize(1);
        assertThat(route.sections().get(0).section().fromStation().code()).isEqualTo("A");
        assertThat(route.sections().get(0).section().toStation().code()).isEqualTo("C");
        // Not COMPLETE, since C != D (the actual destination) - PARTIAL, honestly.
        assertThat(route.completeness()).isEqualTo(RouteCompleteness.PARTIAL);
    }
}
