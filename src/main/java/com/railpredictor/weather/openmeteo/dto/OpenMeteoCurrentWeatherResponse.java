package com.railpredictor.weather.openmeteo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The full response of {@code GET /v1/forecast?...&current=...} - see
 * https://open-meteo.com/en/docs. Only {@code current} is used; every other top-level field
 * (location echo, generation time, units, etc.) is ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenMeteoCurrentWeatherResponse(OpenMeteoCurrentConditions current) {
}
