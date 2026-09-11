package com.railpredictor.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class EvaluationCalibrationPropertiesTest {

    @Test
    void acceptsValidValues() {
        assertDoesNotThrow(() -> new EvaluationCalibrationProperties(true, 30, 0.3, 5.0));
        assertDoesNotThrow(() -> new EvaluationCalibrationProperties(false, 1, 0.01, 0.0));
    }

    @Test
    void rejectsMinimumSampleCountBelowOne() {
        assertThatThrownBy(() -> new EvaluationCalibrationProperties(true, 0, 0.3, 5.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsValidationSplitOutOfOpenRange() {
        assertThatThrownBy(() -> new EvaluationCalibrationProperties(true, 30, 0.0, 5.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EvaluationCalibrationProperties(true, 30, 1.0, 5.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeMinimumPracticalImprovementPercent() {
        assertThatThrownBy(() -> new EvaluationCalibrationProperties(true, 30, 0.3, -1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
