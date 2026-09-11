package com.railpredictor.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.HistoricalAdjustmentProperties;
import org.junit.jupiter.api.Test;

class HistoricalDelayCalculatorTest {

    @Test
    void appliesTheConfiguredWeightWhenSampleCountMeetsTheMinimum() {
        HistoricalDelayCalculator calculator = new HistoricalDelayCalculator(new HistoricalAdjustmentProperties(0.2, 5));

        assertThat(calculator.historicalAdjustmentMinutes(PredictionFixtures.historicalDelay(15.0, 40))).isEqualTo(3);
    }

    @Test
    void sampleCountExactlyAtTheMinimumStillApplies() {
        HistoricalDelayCalculator calculator = new HistoricalDelayCalculator(new HistoricalAdjustmentProperties(0.2, 5));

        assertThat(calculator.historicalAdjustmentMinutes(PredictionFixtures.historicalDelay(15.0, 5))).isEqualTo(3);
    }

    @Test
    void belowMinimumSampleCountGivesNullRatherThanAFabricatedZero() {
        // null (not 0) is the "not a usable fallback" signal PredictionEngine needs to correctly
        // report HistoricalAdjustmentSource.NONE instead of a misleadingly-real STATION_FALLBACK.
        HistoricalDelayCalculator calculator = new HistoricalDelayCalculator(new HistoricalAdjustmentProperties(0.2, 5));

        assertThat(calculator.historicalAdjustmentMinutes(PredictionFixtures.historicalDelay(100.0, 4))).isNull();
    }

    @Test
    void zeroWeightAlwaysGivesZeroWhenSamplesAreOtherwiseSufficient() {
        HistoricalDelayCalculator calculator = new HistoricalDelayCalculator(new HistoricalAdjustmentProperties(0.0, 0));

        assertThat(calculator.historicalAdjustmentMinutes(PredictionFixtures.historicalDelay(100.0, 999))).isZero();
    }
}
