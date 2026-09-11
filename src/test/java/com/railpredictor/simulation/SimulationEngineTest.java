package com.railpredictor.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.CascadeProperties;
import com.railpredictor.config.RecoveryProperties;
import com.railpredictor.model.domain.DisruptionResult;
import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.SimulationContext;
import com.railpredictor.model.domain.SimulationResult;
import com.railpredictor.model.enums.DisruptionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationEngineTest {

    private static DisruptionModel fixedResult(DisruptionResult result) {
        return (train, section, context) -> result;
    }

    /** A cascade engine that never triggers, for tests that aren't about cascading. */
    private static CascadeEngine noCascade() {
        return new CascadeEngine(new CascadeProperties(Integer.MAX_VALUE, 0.0, 1, 0, 1));
    }

    /** A recovery model that never recovers anything, for tests that aren't about recovery. */
    private static RecoveryModel noRecovery() {
        return new RecoveryModel(new RecoveryProperties(0.0, 1.0, 0.0, 0));
    }

    @Test
    void noRegisteredModelsProducesAnEmptyZeroResult() {
        SimulationEngine engine = new SimulationEngine(List.of(), noCascade(), noRecovery());

        SimulationResult result = engine.simulate(SimulationFixtures.allDisabled(1L));

        assertThat(result.disruptions()).isEmpty();
        assertThat(result.cascadeEffects()).isEmpty();
        assertThat(result.directDisruptionDelayMinutes()).isZero();
        assertThat(result.cascadeDelayMinutes()).isZero();
        assertThat(result.recoveredDelayMinutes()).isZero();
        assertThat(result.netDelayMinutes()).isZero();
    }

    @Test
    void sumsDelayOnlyFromTriggeredModels() {
        DisruptionModel rain = fixedResult(new DisruptionResult(DisruptionType.HEAVY_RAIN, true, 10, "rain"));
        DisruptionModel fog = fixedResult(new DisruptionResult(DisruptionType.DENSE_FOG, true, 15, "fog"));
        DisruptionModel congestion = fixedResult(new DisruptionResult(DisruptionType.HIGH_CONGESTION, false, 0, "no congestion"));
        SimulationEngine engine = new SimulationEngine(List.of(rain, fog, congestion), noCascade(), noRecovery());

        SimulationResult result = engine.simulate(SimulationFixtures.allDisabled(1L));

        assertThat(result.disruptions()).hasSize(3);
        assertThat(result.directDisruptionDelayMinutes()).isEqualTo(25);
        assertThat(result.netDelayMinutes()).isEqualTo(25);
    }

    @Test
    void ignoresDelayMinutesOnAModelThatReportsUntriggeredWithANonZeroValue() {
        // Defensive: a triggered=false result should never contribute delay, even if a
        // (buggy or adversarial) model reports a non-zero delayMinutes alongside it.
        DisruptionModel buggy = fixedResult(new DisruptionResult(DisruptionType.SIGNAL_HALT, false, 99, "should not count"));
        SimulationEngine engine = new SimulationEngine(List.of(buggy), noCascade(), noRecovery());

        SimulationResult result = engine.simulate(SimulationFixtures.allDisabled(1L));

        assertThat(result.directDisruptionDelayMinutes()).isZero();
    }

    @Test
    void passesTheContextsOwnTrainAndSectionToEachModel() {
        LiveTrainData expectedTrain = SimulationFixtures.train(80.0);
        RouteSection expectedSection = SimulationFixtures.section(50.0);
        SimulationContext context = SimulationFixtures.context(false, false, false, null, false, false, null, 1L);

        DisruptionModel spy = (train, section, ctx) -> new DisruptionResult(
                DisruptionType.HIGH_CONGESTION,
                train.equals(expectedTrain) && section.equals(expectedSection) && ctx.equals(context),
                train.equals(expectedTrain) && section.equals(expectedSection) && ctx.equals(context) ? 1 : 0,
                "echo");
        SimulationEngine engine = new SimulationEngine(List.of(spy), noCascade(), noRecovery());

        SimulationResult result = engine.simulate(context);

        assertThat(result.disruptions().get(0).triggered()).isTrue();
    }

    @Test
    void recoveryIsZeroWhenTheRecoveryModelIsConfiguredToRecoverNothing() {
        DisruptionModel rain = fixedResult(new DisruptionResult(DisruptionType.HEAVY_RAIN, true, 10, "rain"));
        SimulationEngine engine = new SimulationEngine(List.of(rain), noCascade(), noRecovery());

        SimulationResult result = engine.simulate(SimulationFixtures.allDisabled(1L));

        assertThat(result.recoveredDelayMinutes()).isZero();
        assertThat(result.netDelayMinutes()).isEqualTo(result.directDisruptionDelayMinutes());
    }

    @Test
    void delegatesToTheRealCascadeEngineUsingTheSummedDirectDelay() {
        // primary=20, factor=0.5, maxDepth/maxAffectedEntities=2 -> depth1=round(20*0.5)=10, depth2=round(10*0.5)=5
        DisruptionModel rain = fixedResult(new DisruptionResult(DisruptionType.HEAVY_RAIN, true, 20, "rain"));
        CascadeEngine activeCascade = new CascadeEngine(new CascadeProperties(0, 0.5, 2, 100, 2));
        SimulationEngine engine = new SimulationEngine(List.of(rain), activeCascade, noRecovery());

        SimulationResult result = engine.simulate(SimulationFixtures.allDisabled(1L));

        assertThat(result.cascadeEffects()).hasSize(2);
        assertThat(result.cascadeDelayMinutes()).isEqualTo(15);
        assertThat(result.netDelayMinutes()).isEqualTo(35);
    }

    @Test
    void delegatesToTheRealRecoveryModelUsingTheAccumulatedDelay() {
        // direct=50, no cascade -> accumulated=50; base fraction 0.2 -> 10 recovered (distance not binding).
        DisruptionModel rain = fixedResult(new DisruptionResult(DisruptionType.HEAVY_RAIN, true, 50, "rain"));
        RecoveryModel activeRecovery = new RecoveryModel(new RecoveryProperties(0.2, 0.5, 10.0, 10));
        SimulationEngine engine = new SimulationEngine(List.of(rain), noCascade(), activeRecovery);

        SimulationResult result = engine.simulate(SimulationFixtures.allDisabled(1L));

        assertThat(result.recoveredDelayMinutes()).isEqualTo(10);
        assertThat(result.netDelayMinutes()).isEqualTo(40);
    }
}
