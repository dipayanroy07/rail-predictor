package com.railpredictor.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.TravelTimeProperties;
import org.junit.jupiter.api.Test;

class TravelTimeCalculatorTest {

    private final TravelTimeCalculator calculator = new TravelTimeCalculator(new TravelTimeProperties(60.0));

    @Test
    void computesMinutesFromDistanceAndSpeed() {
        // 90km at 60km/h = 90 minutes
        var train = PredictionFixtures.train(0, 90.0, 60.0);

        assertThat(calculator.baseTravelTimeMinutes(train)).isEqualTo(90.0);
    }

    @Test
    void unknownDistanceGivesZero() {
        var train = PredictionFixtures.train(0, null, 60.0);

        assertThat(calculator.baseTravelTimeMinutes(train)).isZero();
    }

    @Test
    void zeroDistanceMeansArrivedSoZero() {
        var train = PredictionFixtures.train(0, 0.0, 60.0);

        assertThat(calculator.baseTravelTimeMinutes(train)).isZero();
    }

    @Test
    void unknownSpeedFallsBackToAssumedAverage() {
        // 60km at the assumed 60km/h = 60 minutes
        var train = PredictionFixtures.train(0, 60.0, null);

        assertThat(calculator.baseTravelTimeMinutes(train)).isEqualTo(60.0);
        assertThat(calculator.usedAssumedSpeed(train)).isTrue();
    }

    @Test
    void zeroSpeedFallsBackToAssumedAverage() {
        var train = PredictionFixtures.train(0, 60.0, 0.0);

        assertThat(calculator.baseTravelTimeMinutes(train)).isEqualTo(60.0);
        assertThat(calculator.usedAssumedSpeed(train)).isTrue();
    }

    @Test
    void usedAssumedSpeedIsFalseWhenSpeedIsKnownAndPositive() {
        var train = PredictionFixtures.train(0, 60.0, 80.0);

        assertThat(calculator.usedAssumedSpeed(train)).isFalse();
    }
}
