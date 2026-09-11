package com.railpredictor.railradar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The train's current position, per {@code GET /v1/trains/{number}/live}'s {@code currentLocation}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CurrentLocation(
        String stationCode,
        Integer sequence,
        String status,
        Boolean isHalt,
        Boolean isDiverted,
        Boolean isActualPosition,
        Double segmentProgress,
        Double speedKmh,
        Integer bearingDegrees) {
}
