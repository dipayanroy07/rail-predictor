package com.railpredictor.railwaydisruption;

import com.railpredictor.model.domain.RailwayDisruptionQueryResult;
import com.railpredictor.model.domain.RouteSection;
import java.time.Instant;

/**
 * The only way the rest of the application would access real railway operational disruption data
 * (Phase 18) - mirrors {@code HistoricalSectionDelayProvider}'s exact role and shape: callers
 * depend on this interface, never on a specific vendor, and it answers a query for one (train,
 * section) pair at a reference instant, never a "give me everything" query.
 *
 * <p><b>This interface answers "what disruption is reported to exist", never "how many minutes it
 * should cost".</b> No implementation of this interface may compute a delay estimate - that
 * remains the existing simulation/prediction architecture's job, once (in a future phase) it is
 * deliberately designed to consume this data.
 *
 * <p>Nothing in this application calls this interface yet (Phase 18 establishes only the provider
 * foundation - see docs/architecture.md's Phase 18 notes for why, mirroring
 * {@code HistoricalSectionDelayProvider}'s own Phase 16G precedent of being built one full phase
 * before anything consumed it).
 */
public interface RailwayDisruptionProvider {

    /**
     * @param trainNumber the train this query is for - a route-wide disruption (one with no
     *                    train-specific identity) still matches any {@code trainNumber} querying
     *                    the affected section
     * @param section only {@code fromStation().code()}/{@code toStation().code()} are used - the
     *                same plain station-code section identity {@code HistoricalSectionDelayProvider}
     *                already uses
     * @param referenceInstant the point in time this query means - implementations must never
     *                assume an undated disruption is active; see
     *                {@code RailwayDisruptionStatusClassifier}
     */
    RailwayDisruptionQueryResult getDisruptions(String trainNumber, RouteSection section, Instant referenceInstant);
}
