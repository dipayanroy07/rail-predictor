package com.railpredictor.railradar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One station in a train's full itinerary, as returned in the {@code route} array by
 * {@code GET /v1/trains/{number}/live}. {@code distance} is cumulative kilometers from the
 * origin station. See https://railradar.in/docs/live-train-status.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RouteStop(
        Integer sequence,
        String stationCode,
        String stationName,
        Boolean isHalt,
        Double lat,
        Double lng,
        String scheduledArrival,
        String scheduledDeparture,
        String actualArrival,
        String actualDeparture,
        Integer delayArrival,
        Integer delayDeparture,
        String status,
        Double distance,
        Double speedToNextStationKmph,
        String platform) {
}
