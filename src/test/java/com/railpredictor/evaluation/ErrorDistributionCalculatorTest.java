package com.railpredictor.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.model.domain.ErrorDistribution;
import java.util.List;
import org.junit.jupiter.api.Test;

class ErrorDistributionCalculatorTest {

    private final ErrorDistributionCalculator calculator = new ErrorDistributionCalculator();

    @Test
    void emptyListYieldsZeroPlaceholder() {
        ErrorDistribution result = calculator.compute(List.of());

        assertThat(result.sampleCount()).isZero();
        assertThat(result.medianAbsoluteError()).isZero();
        assertThat(result.p90AbsoluteError()).isZero();
    }

    @Test
    void medianOfOddSampleIsMiddleValue() {
        ErrorDistribution result = calculator.compute(List.of(1, -5, 3));

        assertThat(result.sampleCount()).isEqualTo(3);
        assertThat(result.medianAbsoluteError()).isEqualTo(3.0); // sorted absolutes: 1, 3, 5
    }

    @Test
    void medianOfEvenSampleAveragesTheMiddleTwo() {
        ErrorDistribution result = calculator.compute(List.of(1, -3, 5, -7)); // absolutes sorted: 1, 3, 5, 7

        assertThat(result.medianAbsoluteError()).isEqualTo(4.0);
    }

    @Test
    void p90UsesNearestRankNotInterpolation() {
        // 10 values, absolutes 1..10 - ceil(0.9*10) - 1 = index 8 -> value 9
        List<Integer> errors = List.of(1, -2, 3, -4, 5, -6, 7, -8, 9, -10);

        ErrorDistribution result = calculator.compute(errors);

        assertThat(result.p90AbsoluteError()).isEqualTo(9.0);
    }

    @Test
    void singleValueSampleReturnsThatValueForBothStatistics() {
        ErrorDistribution result = calculator.compute(List.of(-4));

        assertThat(result.sampleCount()).isEqualTo(1);
        assertThat(result.medianAbsoluteError()).isEqualTo(4.0);
        assertThat(result.p90AbsoluteError()).isEqualTo(4.0);
    }
}
