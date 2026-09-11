package com.railpredictor.weather;

import com.railpredictor.config.WeatherMockProperties;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.WeatherData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Returns the same configured reading for every location, tagged {@code source = "mock-provider"}
 * so downstream code never mistakes it for a real observation.
 *
 * <p>Active whenever {@code weather.provider} is {@code mock} or unset (the default) - see
 * {@code OpenMeteoWeatherProvider} (Phase 17) for the real alternative and
 * docs/configuration.md for the provider-selection rules. Exactly one {@link WeatherProvider} bean
 * ever exists; the two are mutually exclusive by construction, not by convention - mirrors
 * {@code MockHistoricalDelayProvider}'s exact pattern.
 */
@Component
@ConditionalOnProperty(name = "weather.provider", havingValue = "mock", matchIfMissing = true)
public class MockWeatherProvider implements WeatherProvider {

    private final WeatherMockProperties properties;

    public MockWeatherProvider(WeatherMockProperties properties) {
        this.properties = properties;
    }

    @Override
    public WeatherData getWeather(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude must be between -90 and 90: " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude must be between -180 and 180: " + longitude);
        }
        return new WeatherData(
                properties.condition(),
                properties.temperatureCelsius(),
                properties.visibilityMeters(),
                properties.precipitationMm(),
                DataProvenance.MOCK);
    }
}
