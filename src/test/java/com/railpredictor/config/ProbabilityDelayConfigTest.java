package com.railpredictor.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProbabilityDelayConfigTest {

    @Test
    void acceptsProbabilityBoundsAndEqualMinMax() {
        assertDoesNotThrow(() -> new ProbabilityDelayConfig(0.0, 5, 5));
        assertDoesNotThrow(() -> new ProbabilityDelayConfig(1.0, 5, 5));
    }

    @Test
    void rejectsProbabilityOutsideZeroToOne() {
        assertThrows(IllegalArgumentException.class, () -> new ProbabilityDelayConfig(-0.1, 5, 10));
        assertThrows(IllegalArgumentException.class, () -> new ProbabilityDelayConfig(1.1, 5, 10));
    }

    @Test
    void rejectsNegativeMinDelay() {
        assertThrows(IllegalArgumentException.class, () -> new ProbabilityDelayConfig(0.5, -1, 10));
    }

    @Test
    void rejectsMaxDelayBelowMinDelay() {
        assertThrows(IllegalArgumentException.class, () -> new ProbabilityDelayConfig(0.5, 10, 5));
    }
}
