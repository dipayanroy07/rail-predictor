package com.railpredictor.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.railpredictor.model.domain.PredictionAccuracyMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;

class PredictionAccuracyCalculatorTest {

    private final PredictionAccuracyCalculator calculator = new PredictionAccuracyCalculator();

    @Test
    void emptyDatasetProducesZeroForEverything() {
        PredictionAccuracyMetrics metrics = calculator.compute(List.of());

        assertThat(metrics.sampleCount()).isZero();
        assertThat(metrics.meanAbsoluteError()).isZero();
        assertThat(metrics.rootMeanSquaredError()).isZero();
        assertThat(metrics.bias()).isZero();
    }

    @Test
    void allZeroErrorsProduceZeroForEverything() {
        PredictionAccuracyMetrics metrics = calculator.compute(List.of(0, 0, 0));

        assertThat(metrics.sampleCount()).isEqualTo(3);
        assertThat(metrics.meanAbsoluteError()).isZero();
        assertThat(metrics.rootMeanSquaredError()).isZero();
        assertThat(metrics.bias()).isZero();
    }

    @Test
    void aSinglePositiveErrorIsItsOwnMaeRmseAndBias() {
        PredictionAccuracyMetrics metrics = calculator.compute(List.of(5));

        assertThat(metrics.sampleCount()).isEqualTo(1);
        assertThat(metrics.meanAbsoluteError()).isEqualTo(5.0);
        assertThat(metrics.rootMeanSquaredError()).isEqualTo(5.0);
        assertThat(metrics.bias()).isEqualTo(5.0);
    }

    @Test
    void aSingleNegativeErrorPreservesTheSignInBiasButNotInMaeOrRmse() {
        PredictionAccuracyMetrics metrics = calculator.compute(List.of(-5));

        assertThat(metrics.meanAbsoluteError()).isEqualTo(5.0);
        assertThat(metrics.rootMeanSquaredError()).isEqualTo(5.0);
        assertThat(metrics.bias()).isEqualTo(-5.0);
    }

    @Test
    void mixedErrorsComputeMaeRmseAndBiasCorrectly() {
        // errors: +4, -2, +6, -8 -> mean(abs) = (4+2+6+8)/4 = 5.0
        // mean(x^2) = (16+4+36+64)/4 = 30.0 -> rmse = sqrt(30) ~= 5.477
        // bias = (4-2+6-8)/4 = 0.0
        PredictionAccuracyMetrics metrics = calculator.compute(List.of(4, -2, 6, -8));

        assertThat(metrics.sampleCount()).isEqualTo(4);
        assertThat(metrics.meanAbsoluteError()).isEqualTo(5.0);
        assertThat(metrics.rootMeanSquaredError()).isCloseTo(Math.sqrt(30.0), within(1e-9));
        assertThat(metrics.bias()).isEqualTo(0.0);
    }

    @Test
    void rmseIsAtLeastAsLargeAsMaeWhenErrorsVary() {
        // RMSE penalizes large errors more heavily - a known mathematical property (Cauchy-Schwarz),
        // worth asserting explicitly since it's a real property of these two specific formulas.
        PredictionAccuracyMetrics metrics = calculator.compute(List.of(1, 1, 1, 20));

        assertThat(metrics.rootMeanSquaredError()).isGreaterThan(metrics.meanAbsoluteError());
    }

    @Test
    void consistentOverPredictionProducesAPositiveBias() {
        PredictionAccuracyMetrics metrics = calculator.compute(List.of(3, 5, 4));

        assertThat(metrics.bias()).isEqualTo(4.0);
    }

    @Test
    void consistentUnderPredictionProducesANegativeBias() {
        PredictionAccuracyMetrics metrics = calculator.compute(List.of(-3, -5, -4));

        assertThat(metrics.bias()).isEqualTo(-4.0);
    }
}
