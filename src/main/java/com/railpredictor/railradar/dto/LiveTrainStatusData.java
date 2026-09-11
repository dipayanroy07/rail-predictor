package com.railpredictor.railradar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** The {@code data} payload of {@code GET /v1/trains/{number}/live}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LiveTrainStatusData(
        String trainNumber,
        String trainName,
        String status,
        Integer delayMinutes,
        String lastUpdatedAt,
        CurrentLocation currentLocation,
        NextHalt nextHalt,
        List<RouteStop> route) {
}
