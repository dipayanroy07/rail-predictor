package com.railpredictor.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SectionAnalysisPropertiesTest {

    @Test
    void acceptsEqualThresholds() {
        assertDoesNotThrow(() -> new SectionAnalysisProperties(10, 10));
    }

    @Test
    void rejectsNegativeNormalThreshold() {
        assertThrows(IllegalArgumentException.class, () -> new SectionAnalysisProperties(-1, 10));
    }

    @Test
    void rejectsBusyThresholdBelowNormalThreshold() {
        assertThrows(IllegalArgumentException.class, () -> new SectionAnalysisProperties(30, 10));
    }
}
