package com.railpredictor.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DisruptionImpactPropertiesTest {

    @Test
    void acceptsSensibleValues() {
        assertDoesNotThrow(() -> new DisruptionImpactProperties(15, 10, 10, 60, 90));
    }

    @Test
    void rejectsNegativeEngineeringBlockDefault() {
        assertThrows(IllegalArgumentException.class, () -> new DisruptionImpactProperties(-1, 10, 10, 60, 90));
    }

    @Test
    void rejectsNegativeSignalFailureDefault() {
        assertThrows(IllegalArgumentException.class, () -> new DisruptionImpactProperties(15, -1, 10, 60, 90));
    }

    @Test
    void rejectsNegativeCongestionDefault() {
        assertThrows(IllegalArgumentException.class, () -> new DisruptionImpactProperties(15, 10, -1, 60, 90));
    }

    @Test
    void rejectsNegativeMaxSingle() {
        assertThrows(IllegalArgumentException.class, () -> new DisruptionImpactProperties(15, 10, 10, -1, 90));
    }

    @Test
    void rejectsNegativeMaxAggregate() {
        assertThrows(IllegalArgumentException.class, () -> new DisruptionImpactProperties(15, 10, 10, 60, -1));
    }

    @Test
    void rejectsMaxAggregateLessThanMaxSingle() {
        assertThrows(IllegalArgumentException.class, () -> new DisruptionImpactProperties(15, 10, 10, 60, 30));
    }

    @Test
    void acceptsMaxAggregateEqualToMaxSingle() {
        assertDoesNotThrow(() -> new DisruptionImpactProperties(15, 10, 10, 60, 60));
    }
}
