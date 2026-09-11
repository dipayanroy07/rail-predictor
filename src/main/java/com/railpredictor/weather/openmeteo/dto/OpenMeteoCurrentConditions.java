package com.railpredictor.weather.openmeteo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code current} block of Open-Meteo's forecast response - see
 * https://open-meteo.com/en/docs. Field names are Open-Meteo's own snake_case; {@code time} is an
 * ISO-8601-ish local-time string with no offset (the app always requests {@code timezone=UTC}, so
 * it is UTC - see {@code OpenMeteoWeatherMapper}), representing when this reading is valid for -
 * not when the HTTP response was received.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenMeteoCurrentConditions(
        String time,
        @JsonProperty("temperature_2m") Double temperature2m,
        Double precipitation,
        Double visibility,
        @JsonProperty("weather_code") Integer weatherCode) {
}
