package com.railpredictor.route;

import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.RemainingRoute;
import com.railpredictor.model.domain.RemainingRouteSection;
import com.railpredictor.model.domain.RemainingRouteStop;
import com.railpredictor.model.domain.RouteCompleteness;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.Station;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Builds route information from a train's own live data alone - no separate route-topology
 * lookup, since {@code LiveTrainData} (via {@code LiveTrainDataMapper}, Phase 16H-1) already
 * carries everything RailRadar itself reported about the remaining stations.
 */
@Component
public class LiveDataRouteProvider implements RouteProvider {

    @Override
    public Optional<RouteSection> currentSection(LiveTrainData train) {
        Objects.requireNonNull(train, "train");
        if (train.nextStation() == null) {
            return Optional.empty();
        }
        return Optional.of(new RouteSection(train.currentStation(), train.nextStation(), null));
    }

    /**
     * See {@link RouteCompleteness}'s own Javadoc for what each outcome means. Derived entirely
     * from {@link LiveTrainData#destinationStation()}/{@link LiveTrainData#remainingRouteStops()}
     * (already built defensively by {@code LiveTrainDataMapper}):
     * <ul>
     *   <li>no destination known at all → {@code UNAVAILABLE}, no sections;</li>
     *   <li>the walk never got past the current station and the current station isn't itself the
     *       destination → {@code UNAVAILABLE} (we know where the train is headed, but couldn't
     *       confirm even one step toward it);</li>
     *   <li>the walk's last confirmed stop <em>is</em> the destination → {@code COMPLETE}
     *       (includes the trivial zero-section "already arrived" case);</li>
     *   <li>otherwise → {@code PARTIAL} - real, honest sections exist, they just don't (yet, as
     *       far as this response shows) reach the confirmed destination.</li>
     * </ul>
     */
    @Override
    public RemainingRoute remainingRoute(LiveTrainData train) {
        Objects.requireNonNull(train, "train");

        Station destination = train.destinationStation();
        List<RemainingRouteStop> stops = train.remainingRouteStops();

        if (destination == null || stops.isEmpty()) {
            return new RemainingRoute(train.currentStation(), null, List.of(), RouteCompleteness.UNAVAILABLE);
        }

        boolean reachesDestination = stops.get(stops.size() - 1).station().code().equals(destination.code());
        if (stops.size() == 1 && !reachesDestination) {
            // Only the current station was confirmed - not even one step toward the destination.
            return new RemainingRoute(train.currentStation(), null, List.of(), RouteCompleteness.UNAVAILABLE);
        }

        List<RemainingRouteSection> sections = new ArrayList<>();
        for (int i = 0; i + 1 < stops.size(); i++) {
            RemainingRouteStop from = stops.get(i);
            RemainingRouteStop to = stops.get(i + 1);
            Double distanceKm = (from.distanceFromOriginKm() != null && to.distanceFromOriginKm() != null)
                    ? to.distanceFromOriginKm() - from.distanceFromOriginKm()
                    : null;
            sections.add(new RemainingRouteSection(
                    new RouteSection(from.station(), to.station(), distanceKm),
                    from.sequence(), to.sequence()));
        }

        RouteCompleteness completeness = reachesDestination ? RouteCompleteness.COMPLETE : RouteCompleteness.PARTIAL;
        return new RemainingRoute(train.currentStation(), destination, List.copyOf(sections), completeness);
    }
}
