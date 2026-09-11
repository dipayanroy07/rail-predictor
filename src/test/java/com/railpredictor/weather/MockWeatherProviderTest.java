package com.railpredictor.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.railpredictor.config.WeatherMockProperties;
import com.railpredictor.model.domain.WeatherData;
import com.railpredictor.model.enums.WeatherCondition;
import org.junit.jupiter.api.Test;

class MockWeatherProviderTest {

    private final MockWeatherProvider provider =
            new MockWeatherProvider(new WeatherMockProperties(WeatherCondition.HEAVY_RAIN, 22.0, 500.0, 12.5));

    @Test
    void returnsTheConfiguredReadingTaggedAsMock() {
        WeatherData weather = provider.getWeather(28.6, 77.2);

        assertThat(weather.condition()).isEqualTo(WeatherCondition.HEAVY_RAIN);
        assertThat(weather.temperatureCelsius()).isEqualTo(22.0);
        assertThat(weather.visibilityMeters()).isEqualTo(500.0);
        assertThat(weather.precipitationMm()).isEqualTo(12.5);
        assertThat(weather.source()).isEqualTo("mock-provider");
        // Phase 17: a mock reading is a fixed constant with no real observation moment - never
        // fabricate a timestamp for it.
        assertThat(weather.observedAt()).isNull();
    }

    @Test
    void returnsTheSameReadingRegardlessOfLocation() {
        assertThat(provider.getWeather(28.6, 77.2)).isEqualTo(provider.getWeather(18.9, 72.8));
    }

    @Test
    void rejectsOutOfRangeLatitudeOrLongitude() {
        assertThrows(IllegalArgumentException.class, () -> provider.getWeather(91.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> provider.getWeather(-91.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> provider.getWeather(0.0, 181.0));
        assertThrows(IllegalArgumentException.class, () -> provider.getWeather(0.0, -181.0));
    }

    @Test
    void acceptsBoundaryLatitudeAndLongitude() {
        assertThat(provider.getWeather(90.0, 180.0)).isNotNull();
        assertThat(provider.getWeather(-90.0, -180.0)).isNotNull();
    }
}
