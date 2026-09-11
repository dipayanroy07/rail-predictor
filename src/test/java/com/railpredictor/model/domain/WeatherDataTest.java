package com.railpredictor.model.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.railpredictor.model.enums.WeatherCondition;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WeatherDataTest {

    @Test
    void theLegacyFiveArgConstructorDefaultsObservedAtToNull() {
        WeatherData weather = new WeatherData(WeatherCondition.CLEAR, 25.0, 10000.0, 0.0, DataProvenance.MOCK);

        assertThat(weather.observedAt()).isNull();
    }

    @Test
    void theCanonicalConstructorPreservesAnExplicitObservedAt() {
        Instant observedAt = Instant.parse("2026-09-10T18:00:00Z");

        WeatherData weather = new WeatherData(
                WeatherCondition.RAIN, 24.0, 6000.0, 3.2, DataProvenance.OPENMETEO, observedAt);

        assertThat(weather.observedAt()).isEqualTo(observedAt);
    }

    @Test
    void rejectsNegativeVisibility() {
        assertThrows(IllegalArgumentException.class, () -> new WeatherData(
                WeatherCondition.CLEAR, 25.0, -1.0, 0.0, DataProvenance.MOCK, null));
    }

    @Test
    void rejectsNegativePrecipitation() {
        assertThrows(IllegalArgumentException.class, () -> new WeatherData(
                WeatherCondition.CLEAR, 25.0, 10000.0, -1.0, DataProvenance.MOCK, null));
    }

    @Test
    void rejectsANullCondition() {
        assertThrows(IllegalArgumentException.class, () -> new WeatherData(
                null, 25.0, 10000.0, 0.0, DataProvenance.MOCK, null));
    }

    @Test
    void rejectsABlankSource() {
        assertThrows(IllegalArgumentException.class, () -> new WeatherData(
                WeatherCondition.CLEAR, 25.0, 10000.0, 0.0, " ", null));
    }

    @Test
    void allowsNullTemperatureVisibilityAndPrecipitation() {
        WeatherData weather = new WeatherData(WeatherCondition.UNKNOWN, null, null, null, DataProvenance.UNAVAILABLE, null);

        assertThat(weather.temperatureCelsius()).isNull();
        assertThat(weather.visibilityMeters()).isNull();
        assertThat(weather.precipitationMm()).isNull();
    }
}
