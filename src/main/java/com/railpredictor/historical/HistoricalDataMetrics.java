package com.railpredictor.historical;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Lightweight, in-memory operational counters for the historical-data pipeline (Phase 16C, item
 * 8) - deliberately not a metrics stack (no Micrometer/Actuator registration): plain counters,
 * readable via their getters (tests) and via the log lines each pipeline component already emits
 * alongside incrementing them. A future phase can wire these into Actuator/Micrometer if real
 * operational visibility is needed; nothing here precludes that.
 */
@Component
public class HistoricalDataMetrics {

    private final AtomicLong observationsReceived = new AtomicLong();
    private final AtomicLong observationsRejected = new AtomicLong();
    private final AtomicLong observationsInserted = new AtomicLong();
    private final AtomicLong observationsUpdated = new AtomicLong();
    private final AtomicLong persistenceFailures = new AtomicLong();
    private final AtomicLong profilesGenerated = new AtomicLong();
    private final AtomicLong profileRefreshFailures = new AtomicLong();
    private final AtomicLong sectionProfilesGenerated = new AtomicLong();
    private final AtomicLong sectionProfileRefreshFailures = new AtomicLong();

    /** One {@code RouteStop} was reached (has an actual arrival) and considered for persistence -
     * before validation, so this includes ones that are about to be rejected. */
    public void observationReceived() {
        observationsReceived.incrementAndGet();
    }

    /** A received observation failed data-quality validation and was not persisted. */
    public void observationRejected() {
        observationsRejected.incrementAndGet();
    }

    /** A new canonical row was inserted. */
    public void observationInserted() {
        observationsInserted.incrementAndGet();
    }

    /** An existing canonical row was updated in place (repeated polling of the same station leg). */
    public void observationUpdated() {
        observationsUpdated.incrementAndGet();
    }

    /** A persistence attempt failed (e.g. a concurrent duplicate insert). */
    public void persistenceFailure() {
        persistenceFailures.incrementAndGet();
    }

    /** A historical delay profile was successfully (re-)computed and persisted. */
    public void profileGenerated() {
        profilesGenerated.incrementAndGet();
    }

    /** Refreshing a single profile failed and was skipped. */
    public void profileRefreshFailure() {
        profileRefreshFailures.incrementAndGet();
    }

    /** A historical section delay profile (Phase 16F) was successfully (re-)computed and persisted. */
    public void sectionProfileGenerated() {
        sectionProfilesGenerated.incrementAndGet();
    }

    /** Refreshing a train's section profiles failed and was skipped. */
    public void sectionProfileRefreshFailure() {
        sectionProfileRefreshFailures.incrementAndGet();
    }

    public long observationsReceived() {
        return observationsReceived.get();
    }

    public long observationsRejected() {
        return observationsRejected.get();
    }

    public long observationsInserted() {
        return observationsInserted.get();
    }

    public long observationsUpdated() {
        return observationsUpdated.get();
    }

    public long persistenceFailures() {
        return persistenceFailures.get();
    }

    public long profilesGenerated() {
        return profilesGenerated.get();
    }

    public long profileRefreshFailures() {
        return profileRefreshFailures.get();
    }

    public long sectionProfilesGenerated() {
        return sectionProfilesGenerated.get();
    }

    public long sectionProfileRefreshFailures() {
        return sectionProfileRefreshFailures.get();
    }
}
