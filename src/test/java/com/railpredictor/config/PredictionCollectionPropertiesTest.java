package com.railpredictor.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PredictionCollectionPropertiesTest {

    @Test
    void acceptsAValidConfiguration() {
        PredictionCollectionProperties properties = new PredictionCollectionProperties(true, 3600000, 600000);

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.intervalMs()).isEqualTo(3600000);
        assertThat(properties.initialDelayMs()).isEqualTo(600000);
    }

    @Test
    void rejectsANonPositiveIntervalMs() {
        assertThatThrownBy(() -> new PredictionCollectionProperties(true, 0, 600000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PredictionCollectionProperties(true, -1, 600000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANegativeInitialDelayMs() {
        assertThatThrownBy(() -> new PredictionCollectionProperties(true, 3600000, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
