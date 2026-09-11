package com.railpredictor.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.SpeedRestrictionConfig;
import com.railpredictor.model.domain.DisruptionResult;
import com.railpredictor.model.enums.DisruptionType;
import org.junit.jupiter.api.Test;

class SpeedRestrictionModelTest {

    private final SpeedRestrictionModel model =
            new SpeedRestrictionModel(SimulationFixtures.propertiesWithSpeedRestriction(new SpeedRestrictionConfig(10)));

    @Test
    void noRestrictionMeansNotTriggered() {
        var context = SimulationFixtures.context(false, false, false, null, false, false, null, 1L);

        DisruptionResult result = model.simulate(SimulationFixtures.train(100.0), SimulationFixtures.section(100.0), context);

        assertThat(result.type()).isEqualTo(DisruptionType.SPEED_RESTRICTION);
        assertThat(result.triggered()).isFalse();
        assertThat(result.delayMinutes()).isZero();
    }

    @Test
    void computesDelayFromDistanceAndSpeedWhenBothAreKnown() {
        // 100km at 100km/h = 60 min; at a 50km/h restriction = 120 min -> 60 min extra.
        var context = SimulationFixtures.context(false, false, false, 50.0, false, false, null, 1L);

        DisruptionResult result = model.simulate(SimulationFixtures.train(100.0), SimulationFixtures.section(100.0), context);

        assertThat(result.triggered()).isTrue();
        assertThat(result.delayMinutes()).isEqualTo(60);
    }

    @Test
    void fallsBackToFlatDelayWhenTrainSpeedIsUnknown() {
        var context = SimulationFixtures.context(false, false, false, 50.0, false, false, null, 1L);

        DisruptionResult result = model.simulate(SimulationFixtures.train(null), SimulationFixtures.section(100.0), context);

        assertThat(result.triggered()).isTrue();
        assertThat(result.delayMinutes()).isEqualTo(10);
    }

    @Test
    void fallsBackToFlatDelayWhenSectionDistanceIsUnknown() {
        var context = SimulationFixtures.context(false, false, false, 50.0, false, false, null, 1L);

        DisruptionResult result = model.simulate(SimulationFixtures.train(100.0), SimulationFixtures.section(null), context);

        assertThat(result.triggered()).isTrue();
        assertThat(result.delayMinutes()).isEqualTo(10);
    }

    @Test
    void fallsBackToFlatDelayWhenRestrictionIsNotActuallySlowerThanCurrentSpeed() {
        var context = SimulationFixtures.context(false, false, false, 120.0, false, false, null, 1L);

        DisruptionResult result = model.simulate(SimulationFixtures.train(100.0), SimulationFixtures.section(100.0), context);

        assertThat(result.triggered()).isTrue();
        assertThat(result.delayMinutes()).isEqualTo(10);
    }
}
