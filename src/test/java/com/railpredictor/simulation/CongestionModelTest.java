package com.railpredictor.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.ProbabilityDelayConfig;
import com.railpredictor.model.domain.DisruptionResult;
import com.railpredictor.model.enums.DisruptionType;
import org.junit.jupiter.api.Test;

class CongestionModelTest {

    @Test
    void disabledNeverTriggers() {
        CongestionModel model = new CongestionModel(
                SimulationFixtures.propertiesWithHighCongestion(new ProbabilityDelayConfig(1.0, 5, 15)));
        var context = SimulationFixtures.context(false, false, false, null, false, false, null, 1L);

        DisruptionResult result = model.simulate(SimulationFixtures.train(80.0), SimulationFixtures.section(50.0), context);

        assertThat(result.type()).isEqualTo(DisruptionType.HIGH_CONGESTION);
        assertThat(result.triggered()).isFalse();
        assertThat(result.delayMinutes()).isZero();
    }

    @Test
    void enabledWithProbabilityOneAlwaysTriggersWithDelayInRange() {
        CongestionModel model = new CongestionModel(
                SimulationFixtures.propertiesWithHighCongestion(new ProbabilityDelayConfig(1.0, 5, 15)));
        var context = SimulationFixtures.context(false, false, true, null, false, false, null, 1L);

        DisruptionResult result = model.simulate(SimulationFixtures.train(80.0), SimulationFixtures.section(50.0), context);

        assertThat(result.triggered()).isTrue();
        assertThat(result.delayMinutes()).isBetween(5, 15);
    }

    @Test
    void enabledWithProbabilityZeroNeverTriggers() {
        CongestionModel model = new CongestionModel(
                SimulationFixtures.propertiesWithHighCongestion(new ProbabilityDelayConfig(0.0, 5, 15)));
        var context = SimulationFixtures.context(false, false, true, null, false, false, null, 1L);

        DisruptionResult result = model.simulate(SimulationFixtures.train(80.0), SimulationFixtures.section(50.0), context);

        assertThat(result.triggered()).isFalse();
    }
}
