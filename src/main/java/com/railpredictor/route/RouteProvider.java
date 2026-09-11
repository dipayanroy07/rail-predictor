package com.railpredictor.route;

import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.RemainingRoute;
import com.railpredictor.model.domain.RouteSection;
import java.util.Optional;

/**
 * Resolves route information for a train from its own live data. Empty/{@code UNAVAILABLE} when
 * the train has no next station (e.g. terminated) - there is no "current section"/remaining route
 * to speak of in that case.
 */
public interface RouteProvider {

    Optional<RouteSection> currentSection(LiveTrainData train);

    /**
     * The remaining sections from the train's current position to its destination, as far as
     * they can be reconstructed from already-known live data (Phase 16H-1) - see
     * {@link RemainingRoute}'s own Javadoc for exactly what "as far as possible" means and why it
     * is never fabricated beyond what was actually reported.
     */
    RemainingRoute remainingRoute(LiveTrainData train);
}
