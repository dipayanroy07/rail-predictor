package com.railpredictor.config;

import com.railpredictor.model.enums.WeatherCondition;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The single fixed weather reading {@code MockWeatherProvider} returns for every location, until
 * a real weather API is wired in (Phase 17). Mock data, not a measurement - see
 * docs/architecture.md.
 */
@ConfigurationProperties(prefix = "weather.mock")
public record WeatherMockProperties(
        WeatherCondition condition,
        double temperatureCelsius,
        double visibilityMeters,
        double precipitationMm) {

    public WeatherMockProperties {
        if (condition == null) {
            throw new IllegalArgumentException("weather.mock.condition must not be null");
        }
        if (visibilityMeters < 0) {
            throw new IllegalArgumentException("weather.mock.visibility-meters must not be negative: " + visibilityMeters);
        }
        if (precipitationMm < 0) {
            throw new IllegalArgumentException("weather.mock.precipitation-mm must not be negative: " + precipitationMm);
        }
    }
}
