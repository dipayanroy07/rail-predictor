package com.railpredictor.model.domain;

import java.util.List;

/**
 * The result of asking a {@code RailwayDisruptionProvider} what real operational disruptions are
 * currently known for one (train, section) pair (Phase 18) - mirrors
 * {@code SectionHistoricalDelayResult}'s exact shape (identity fields + a status + the payload),
 * adapted for the {@link RailwayDisruptionAvailability#AVAILABLE}/
 * {@link RailwayDisruptionAvailability#UNAVAILABLE} distinction this domain needs instead.
 *
 * <p>{@code disruptions} is empty whenever {@code availability} is
 * {@link RailwayDisruptionAvailability#UNAVAILABLE} (enforced below) - never populated with stale
 * or guessed data for that case.
 */
public record RailwayDisruptionQueryResult(
        String trainNumber,
        String fromStationCode,
        String toStationCode,
        RailwayDisruptionAvailability availability,
        List<RailwayDisruption> disruptions) {

    public RailwayDisruptionQueryResult {
        trainNumber = Guard.requireNonBlank(trainNumber, "trainNumber");
        fromStationCode = Guard.requireNonBlank(fromStationCode, "fromStationCode");
        toStationCode = Guard.requireNonBlank(toStationCode, "toStationCode");
        availability = Guard.requireNonNull(availability, "availability");
        disruptions = List.copyOf(Guard.requireNonNull(disruptions, "disruptions"));
        if (availability == RailwayDisruptionAvailability.UNAVAILABLE && !disruptions.isEmpty()) {
            throw new IllegalArgumentException("disruptions must be empty when availability is UNAVAILABLE");
        }
    }
}
