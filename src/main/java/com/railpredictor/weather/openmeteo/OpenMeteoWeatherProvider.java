package com.railpredictor.weather.openmeteo;

import com.railpredictor.model.domain.WeatherData;
import com.railpredictor.weather.WeatherProvider;
import com.railpredictor.weather.openmeteo.dto.OpenMeteoCurrentWeatherResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The {@link WeatherProvider} backed by the real Open-Meteo API (Phase 17) - active only when
 * {@code weather.provider=openmeteo} is explicitly set (see application.properties/
 * docs/configuration.md), mirroring {@code MockHistoricalDelayProvider}/
 * {@code PostgresHistoricalDelayProvider}'s exact deterministic-selection pattern: exactly one
 * {@link WeatherProvider} bean ever exists, selected by this property, never a silent fallback
 * between mock and real.
 *
 * <p>A failure anywhere in {@link OpenMeteoClient}/{@link OpenMeteoWeatherMapper} propagates
 * unchanged as a {@code WeatherUnavailableException} - this class does not catch it. {@code
 * PredictionService} is where that failure is caught and degraded to "no weather data" (see its
 * own Javadoc) - this provider never substitutes mock data itself.
 */
@Component
@ConditionalOnProperty(name = "weather.provider", havingValue = "openmeteo")
public class OpenMeteoWeatherProvider implements WeatherProvider {

    private final OpenMeteoClient client;
    private final OpenMeteoWeatherMapper mapper;

    OpenMeteoWeatherProvider(OpenMeteoClient client, OpenMeteoWeatherMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public WeatherData getWeather(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude must be between -90 and 90: " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude must be between -180 and 180: " + longitude);
        }
        OpenMeteoCurrentWeatherResponse response = client.fetchCurrentWeather(latitude, longitude);
        return mapper.toWeatherData(response);
    }
}
