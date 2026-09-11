package com.railpredictor.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code weather.openmeteo.*} configuration keys (see application.properties). Mirrors
 * {@link RailRadarProperties}'s shape.
 *
 * <p>{@code apiKey} is never hardcoded and must come from the {@code WEATHER_OPENMETEO_API_KEY}
 * environment variable - though Open-Meteo's own free/non-commercial tier needs none at all (see
 * docs/configuration.md); this field only matters if a commercial Open-Meteo plan is ever used.
 *
 * <p>{@code denseFogVisibilityMeters} is the one genuinely new classification threshold this phase
 * introduces (Open-Meteo's WMO weather code has a single "fog" code with no light/dense
 * distinction - only {@code visibility} can supply that) - see {@code OpenMeteoWeatherMapper} for
 * exactly how it's used, and docs/prediction-model.md for why 200m (a standard meteorological
 * "dense fog" cutoff) is the default.
 */
@ConfigurationProperties(prefix = "weather.openmeteo")
public record OpenMeteoProperties(String baseUrl, String apiKey, Duration timeout, double denseFogVisibilityMeters) {

    public OpenMeteoProperties {
        if (denseFogVisibilityMeters < 0) {
            throw new IllegalArgumentException(
                    "weather.openmeteo.dense-fog-visibility-meters must not be negative: " + denseFogVisibilityMeters);
        }
    }
}
