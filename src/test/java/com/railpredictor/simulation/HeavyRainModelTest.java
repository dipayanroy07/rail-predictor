package com.railpredictor.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.ProbabilityDelayConfig;
import com.railpredictor.model.domain.DisruptionResult;
import com.railpredictor.model.domain.WeatherData;
import com.railpredictor.model.enums.DisruptionType;
import com.railpredictor.model.enums.WeatherCondition;
import org.junit.jupiter.api.Test;

class HeavyRainModelTest {

    @Test
    void disabledNeverTriggersEvenWithMatchingWeather() {
        HeavyRainModel model = new HeavyRainModel(
                SimulationFixtures.propertiesWithHeavyRain(new ProbabilityDelayConfig(1.0, 5, 20)));
        WeatherData rain = new WeatherData(WeatherCondition.HEAVY_RAIN, 20.0, 1000.0, 30.0, "mock-provider");
        var context = SimulationFixtures.context(false, false, false, null, false, false, rain, 1L);

        DisruptionResult result = model.simulate(SimulationFixtures.train(80.0), SimulationFixtures.section(50.0), context);

        assertThat(result.triggered()).isFalse();
        assertThat(result.delayMinutes()).isZero();
    }

    @Test
    void matchingWeatherTriggersEvenWithZeroConfiguredProbability() {
        HeavyRainModel model = new HeavyRainModel(
                SimulationFixtures.propertiesWithHeavyRain(new ProbabilityDelayConfig(0.0, 5, 20)));
        WeatherData rain = new WeatherData(WeatherCondition.HEAVY_RAIN, 20.0, 1000.0, 30.0, "mock-provider");
        var context = SimulationFixtures.context(true, false, false, null, false, false, rain, 1L);

        DisruptionResult result = model.simulate(SimulationFixtures.train(80.0), SimulationFixtures.section(50.0), context);

        assertThat(result.type()).isEqualTo(DisruptionType.HEAVY_RAIN);
        assertThat(result.triggered()).isTrue();
        assertThat(result.delayMinutes()).isBetween(5, 20);
    }

    @Test
    void nonMatchingWeatherFallsBackToProbabilityRoll() {
        HeavyRainModel model = new HeavyRainModel(
                SimulationFixtures.propertiesWithHeavyRain(new ProbabilityDelayConfig(0.0, 5, 20)));
        WeatherData clear = new WeatherData(WeatherCondition.CLEAR, 20.0, 10000.0, 0.0, "mock-provider");
        var context = SimulationFixtures.context(true, false, false, null, false, false, clear, 1L);

        DisruptionResult result = model.simulate(SimulationFixtures.train(80.0), SimulationFixtures.section(50.0), context);

        assertThat(result.triggered()).isFalse();
    }

    @Test
    void sameContextProducesTheSameResultEveryTime() {
        HeavyRainModel model = new HeavyRainModel(
                SimulationFixtures.propertiesWithHeavyRain(new ProbabilityDelayConfig(0.5, 5, 20)));
        var context = SimulationFixtures.context(true, false, false, null, false, false, null, 99L);

        DisruptionResult first = model.simulate(SimulationFixtures.train(80.0), SimulationFixtures.section(50.0), context);
        DisruptionResult second = model.simulate(SimulationFixtures.train(80.0), SimulationFixtures.section(50.0), context);

        assertThat(first).isEqualTo(second);
    }
}
