package com.railpredictor.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DelayCalculatorTest {

    private final DelayCalculator calculator = new DelayCalculator();

    @Test
    void extractsNetDelayMinutesUnchanged() {
        assertThat(calculator.predictedExtraDelayMinutes(PredictionFixtures.simulationResult(15, 3, 10, 8))).isEqualTo(8);
    }

    @Test
    void zeroNetDelayPassesThroughAsZero() {
        assertThat(calculator.predictedExtraDelayMinutes(PredictionFixtures.simulationResult(0, 0, 0, 0))).isZero();
    }
}
