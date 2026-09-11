package com.railpredictor.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ConfidencePropertiesTest {

    private static final ConfidenceWeights WEIGHTS = new ConfidenceWeights(15, 10, 15, 10, 15, 10, 25);
    private static final ConfidenceThresholds THRESHOLDS = new ConfidenceThresholds(80, 60, 35);

    @Test
    void rejectsMissingSubConfig() {
        assertThrows(NullPointerException.class, () -> new ConfidenceProperties(null, THRESHOLDS, 1));
        assertThrows(NullPointerException.class, () -> new ConfidenceProperties(WEIGHTS, null, 1));
    }

    @Test
    void rejectsNegativeMaxStableTriggeredDisruptions() {
        assertThrows(IllegalArgumentException.class, () -> new ConfidenceProperties(WEIGHTS, THRESHOLDS, -1));
    }
}
