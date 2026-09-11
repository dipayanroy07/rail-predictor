package com.railpredictor.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.ProbabilityDelayConfig;
import com.railpredictor.model.domain.DisruptionResult;
import com.railpredictor.model.enums.DisruptionType;
import org.junit.jupiter.api.Test;

class SignalHaltModelTest {

    @Test
    void disabledNeverTriggers() {
        SignalHaltModel model = new SignalHaltModel(
                SimulationFixtures.propertiesWithSignalHalt(new ProbabilityDelayConfig(1.0, 3, 10)));
        var context = SimulationFixtures.context(false, false, false, null, false, false, null, 1L);

        DisruptionResult result = model.simulate(SimulationFixtures.train(80.0), SimulationFixtures.section(50.0), context);

        assertThat(result.type()).isEqualTo(DisruptionType.SIGNAL_HALT);
        assertThat(result.triggered()).isFalse();
    }

    @Test
    void enabledWithProbabilityOneAlwaysTriggersWithDelayInRange() {
        SignalHaltModel model = new SignalHaltModel(
                SimulationFixtures.propertiesWithSignalHalt(new ProbabilityDelayConfig(1.0, 3, 10)));
        var context = SimulationFixtures.context(false, false, false, null, false, true, null, 1L);

        DisruptionResult result = model.simulate(SimulationFixtures.train(80.0), SimulationFixtures.section(50.0), context);

        assertThat(result.triggered()).isTrue();
        assertThat(result.delayMinutes()).isBetween(3, 10);
    }
}
