package com.railpredictor.disruptionimpact;

import com.railpredictor.model.domain.CalibrationStatus;
import com.railpredictor.model.domain.DisruptionImpact;
import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.RailwayDisruption;
import com.railpredictor.model.domain.RouteSection;

/**
 * Translates one real, currently-active {@link RailwayDisruption} into a predicted delay
 * contribution (Phase 19) - the policy boundary between "what disruption is reported to exist"
 * ({@code railwaydisruption} package) and "how many minutes it should cost" (this package).
 *
 * <p>Operates purely on domain objects - never on RailRadar/Open-Meteo DTOs, JPA entities, or REST
 * shapes. Callers are responsible for establishing that {@code disruption} is actually relevant
 * <em>now</em> (see {@code railwaydisruption.RailwayDisruptionStatusClassifier}) before calling
 * this - this interface does not re-check temporal validity itself.
 */
public interface DisruptionImpactPolicy {

    /**
     * @param disruption the real disruption to evaluate - assumed by the caller to already be
     *                   currently active
     * @param section the section this disruption was matched against
     * @param train the train's current live state (for inputs like current speed)
     */
    DisruptionImpact evaluate(RailwayDisruption disruption, RouteSection section, LiveTrainData train);

    /** Whether this policy's numbers are backed by real evaluated evidence - see
     * {@link CalibrationStatus}'s own Javadoc. */
    CalibrationStatus calibrationStatus();
}
