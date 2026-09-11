package com.railpredictor.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.ProbabilityDelayConfig;
import com.railpredictor.model.domain.DisruptionResult;
import com.railpredictor.model.domain.WeatherData;
import com.railpredictor.model.enums.DisruptionType;
import com.railpredictor.model.enums.WeatherCondition;
import org.junit.jupiter.api.Test;

class DenseFogModelTest {

    @Test
    void disabledNeverTriggers() {
        DenseFogModel model = new DenseFogModel(
                SimulationFixtures.propertiesWithDenseFog(new ProbabilityDelayConfig(1.0, 10, 30)));
        var context = SimulationFixtures.context(false, false, false, null, false, false, null, 1L);

        DisruptionResult result = model.simulate(SimulationFixtures.train(80.0), SimulationFixtures.section(50.0), context);

        assertThat(result.triggered()).isFalse();
    }

    @Test
    void denseFogWeatherTriggersEvenWithZeroConfiguredProbability() {
        DenseFogModel model = new DenseFogModel(
                SimulationFixtures.propertiesWithDenseFog(new ProbabilityDelayConfig(0.0, 10, 30)));
        WeatherData fog = new WeatherData(WeatherCondition.DENSE_FOG, 10.0, 50.0, 0.0, "mock-provider");
        var context = SimulationFixtures.context(false, true, false, null, false, false, fog, 1L);

        DisruptionResult result = model.simulate(SimulationFixtures.train(80.0), SimulationFixtures.section(50.0), context);

        assertThat(result.type()).isEqualTo(DisruptionType.DENSE_FOG);
        assertThat(result.triggered()).isTrue();
        assertThat(result.delayMinutes()).isBetween(10, 30);
    }

    @Test
    void plainFogIsNotStrongEnoughEvidenceOfDenseFog() {
        DenseFogModel model = new DenseFogModel(
                SimulationFixtures.propertiesWithDenseFog(new ProbabilityDelayConfig(0.0, 10, 30)));
        WeatherData mildFog = new WeatherData(WeatherCondition.FOG, 10.0, 800.0, 0.0, "mock-provider");
        var context = SimulationFixtures.context(false, true, false, null, false, false, mildFog, 1L);

        DisruptionResult result = model.simulate(SimulationFixtures.train(80.0), SimulationFixtures.section(50.0), context);

        assertThat(result.triggered()).isFalse();
    }
}
