package com.railpredictor.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.RecoveryProperties;
import com.railpredictor.model.domain.WeatherData;
import com.railpredictor.model.enums.WeatherCondition;
import org.junit.jupiter.api.Test;

class RecoveryModelTest {

    @Test
    void nothingToRecoverWhenAccumulatedDelayIsZeroOrLess() {
        RecoveryModel model = new RecoveryModel(new RecoveryProperties(0.2, 0.5, 10.0, 10));

        assertThat(model.recover(0, SimulationFixtures.allDisabled(1L))).isZero();
    }

    @Test
    void recoversBaseFractionWhenDistanceIsNotTheBindingConstraint() {
        // train's remainingDistanceKm is 25.0 (SimulationFixtures); 25 * 10.0/km = 250, far above
        // the 50 * 0.2 = 10 the base fraction would recover, so the base fraction binds.
        RecoveryModel model = new RecoveryModel(new RecoveryProperties(0.2, 0.5, 10.0, 10));

        int recovered = model.recover(50, SimulationFixtures.allDisabled(1L));

        assertThat(recovered).isEqualTo(10);
    }

    @Test
    void adverseWeatherHalvesRecoveryUnderTheConfiguredMultiplier() {
        RecoveryModel model = new RecoveryModel(new RecoveryProperties(0.2, 0.5, 10.0, 10));
        WeatherData heavyRain = new WeatherData(WeatherCondition.HEAVY_RAIN, 20.0, 500.0, 20.0, "mock-provider");
        var context = SimulationFixtures.context(false, false, false, null, false, false, heavyRain, 1L);

        assertThat(model.recover(50, context)).isEqualTo(5);
    }

    @Test
    void mildWeatherIsNotTreatedAsAdverse() {
        RecoveryModel model = new RecoveryModel(new RecoveryProperties(0.2, 0.5, 10.0, 10));
        WeatherData mildFog = new WeatherData(WeatherCondition.FOG, 15.0, 800.0, 0.0, "mock-provider");
        var context = SimulationFixtures.context(false, false, false, null, false, false, mildFog, 1L);

        assertThat(model.recover(50, context)).isEqualTo(10);
    }

    @Test
    void smallRemainingDistanceCapsRecoveryBelowTheBaseFraction() {
        // 1km remaining * 0.05/km = 0.05 -> rounds to 0, well below the 50*0.2=10 base fraction.
        RecoveryModel model = new RecoveryModel(new RecoveryProperties(0.2, 0.5, 0.05, 10));
        var trainWithLittleDistanceLeft = new com.railpredictor.model.domain.LiveTrainData(
                "12345", "Test Express", com.railpredictor.model.enums.TrainStatus.RUNNING, 5,
                SimulationFixtures.station("NDLS"), SimulationFixtures.station("GZB"), 10.0, 1.0, 80.0);
        var context = new com.railpredictor.model.domain.SimulationContext(
                trainWithLittleDistanceLeft, SimulationFixtures.section(50.0), null, null,
                false, false, false, null, false, false, 1L);

        assertThat(model.recover(50, context)).isZero();
    }

    @Test
    void unknownRemainingDistanceUsesTheFlatFallbackCap() {
        RecoveryModel model = new RecoveryModel(new RecoveryProperties(1.0, 1.0, 100.0, 7));
        var context = SimulationFixtures.context(false, false, false, null, false, false, null, 1L);
        var trainWithUnknownDistance = new com.railpredictor.model.domain.LiveTrainData(
                "12345", "Test Express", com.railpredictor.model.enums.TrainStatus.RUNNING, 5,
                SimulationFixtures.station("NDLS"), SimulationFixtures.station("GZB"), 10.0, null, 80.0);
        var contextWithUnknownDistance = new com.railpredictor.model.domain.SimulationContext(
                trainWithUnknownDistance, context.section(), null, null,
                false, false, false, null, false, false, 1L);

        assertThat(model.recover(50, contextWithUnknownDistance)).isEqualTo(7);
    }

    @Test
    void neverRecoversMoreThanTheAccumulatedDelay() {
        RecoveryModel model = new RecoveryModel(new RecoveryProperties(1.0, 1.0, 1000.0, 1000));

        assertThat(model.recover(5, SimulationFixtures.allDisabled(1L))).isEqualTo(5);
    }
}
