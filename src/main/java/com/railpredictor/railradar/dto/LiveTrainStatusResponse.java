package com.railpredictor.railradar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The full envelope every RailRadar response is wrapped in: {@code {success, data, meta}}.
 * {@code data} is null when {@code success} is false.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LiveTrainStatusResponse(boolean success, LiveTrainStatusData data, ResponseMeta meta) {
}
