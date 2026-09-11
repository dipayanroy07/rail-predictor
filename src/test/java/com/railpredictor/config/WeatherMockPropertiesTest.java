package com.railpredictor.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.railpredictor.model.enums.WeatherCondition;
import org.junit.jupiter.api.Test;

class WeatherMockPropertiesTest {

    @Test
    void acceptsZeroVisibilityAndPrecipitation() {
        assertDoesNotThrow(() -> new WeatherMockProperties(WeatherCondition.DENSE_FOG, 10.0, 0.0, 0.0));
    }

    @Test
    void rejectsNullCondition() {
        assertThrows(IllegalArgumentException.class, () -> new WeatherMockProperties(null, 10.0, 100.0, 0.0));
    }

    @Test
    void rejectsNegativeVisibilityOrPrecipitation() {
        assertThrows(IllegalArgumentException.class,
                () -> new WeatherMockProperties(WeatherCondition.CLEAR, 10.0, -1.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new WeatherMockProperties(WeatherCondition.CLEAR, 10.0, 100.0, -1.0));
    }

    @Test
    void allowsNegativeTemperature() {
        assertDoesNotThrow(() -> new WeatherMockProperties(WeatherCondition.CLEAR, -15.0, 10000.0, 0.0));
    }
}
