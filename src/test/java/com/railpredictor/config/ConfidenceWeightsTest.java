package com.railpredictor.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ConfidenceWeightsTest {

    @Test
    void acceptsAllZeroWeights() {
        assertDoesNotThrow(() -> new ConfidenceWeights(0, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void rejectsAnyNegativeWeight() {
        assertThrows(IllegalArgumentException.class, () -> new ConfidenceWeights(-1, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ConfidenceWeights(0, 0, 0, 0, 0, 0, -1));
    }
}
