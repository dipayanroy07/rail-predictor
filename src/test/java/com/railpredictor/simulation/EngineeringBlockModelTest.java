package com.railpredictor.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.ProbabilityDelayConfig;
import com.railpredictor.model.domain.DisruptionResult;
import com.railpredictor.model.enums.DisruptionType;
import org.junit.jupiter.api.Test;

class EngineeringBlockModelTest {

    @Test
    void disabledNeverTriggers() {
        EngineeringBlockModel model = new EngineeringBlockModel(
                SimulationFixtures.propertiesWithEngineeringBlock(new ProbabilityDelayConfig(1.0, 15, 45)));
        var context = SimulationFixtures.context(false, false, false, null, false, false, null, 1L);

        DisruptionResult result = model.simulate(SimulationFixtures.train(80.0), SimulationFixtures.section(50.0), context);

        assertThat(result.type()).isEqualTo(DisruptionType.ENGINEERING_BLOCK);
        assertThat(result.triggered()).isFalse();
    }

    @Test
    void enabledWithProbabilityOneAlwaysTriggersWithDelayInRange() {
        EngineeringBlockModel model = new EngineeringBlockModel(
                SimulationFixtures.propertiesWithEngineeringBlock(new ProbabilityDelayConfig(1.0, 15, 45)));
        var context = SimulationFixtures.context(false, false, false, null, true, false, null, 1L);

        DisruptionResult result = model.simulate(SimulationFixtures.train(80.0), SimulationFixtures.section(50.0), context);

        assertThat(result.triggered()).isTrue();
        assertThat(result.delayMinutes()).isBetween(15, 45);
    }
}
