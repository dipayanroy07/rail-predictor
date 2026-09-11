package com.railpredictor.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OpenMeteoPropertiesTest {

    @Test
    void acceptsAnEmptyApiKeyForTheFreeNonCommercialTier() {
        assertDoesNotThrow(() -> new OpenMeteoProperties("https://api.open-meteo.com", "", Duration.ofSeconds(5), 200.0));
    }

    @Test
    void acceptsAConfiguredApiKeyForTheCommercialTier() {
        assertDoesNotThrow(() -> new OpenMeteoProperties("https://customer-api.open-meteo.com", "secret", Duration.ofSeconds(5), 200.0));
    }

    @Test
    void rejectsANegativeDenseFogVisibilityThreshold() {
        assertThrows(IllegalArgumentException.class,
                () -> new OpenMeteoProperties("https://api.open-meteo.com", "", Duration.ofSeconds(5), -1.0));
    }

    @Test
    void acceptsAZeroDenseFogVisibilityThreshold() {
        assertDoesNotThrow(() -> new OpenMeteoProperties("https://api.open-meteo.com", "", Duration.ofSeconds(5), 0.0));
    }
}
