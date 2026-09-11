package com.railpredictor.simulation;

import com.railpredictor.config.DisruptionProperties;
import com.railpredictor.config.ProbabilityDelayConfig;
import com.railpredictor.model.domain.DisruptionResult;
import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.SimulationContext;
import com.railpredictor.model.enums.DisruptionType;
import java.util.Objects;
import java.util.Random;
import org.springframework.stereotype.Component;

/**
 * Simulates a signal-halt delay. No real signalling feed exists yet, so this is purely
 * {@code context.signalHaltEnabled()} as the master switch, gated by a configured trigger
 * probability, with delay magnitude drawn from a configured range.
 */
@Component
public class SignalHaltModel implements DisruptionModel {

    private final ProbabilityDelayConfig config;

    public SignalHaltModel(DisruptionProperties properties) {
        this.config = properties.signalHalt();
    }

    @Override
    public DisruptionResult simulate(LiveTrainData train, RouteSection section, SimulationContext context) {
        Objects.requireNonNull(train, "train");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(context, "context");

        if (!context.signalHaltEnabled()) {
            return new DisruptionResult(DisruptionType.SIGNAL_HALT, false, 0,
                    "Signal halt disabled for this simulation run");
        }

        Random random = DisruptionRandom.forModel(context.randomSeed(), DisruptionType.SIGNAL_HALT);
        ProbabilisticDisruption.Outcome outcome = ProbabilisticDisruption.roll(
                true, config.triggerProbability(), config.minDelayMinutes(), config.maxDelayMinutes(), random);
        if (outcome.triggered()) {
            return new DisruptionResult(DisruptionType.SIGNAL_HALT, true, outcome.delayMinutes(),
                    "Simulated signal halt (p=" + config.triggerProbability() + ") added " + outcome.delayMinutes() + " min");
        }
        return new DisruptionResult(DisruptionType.SIGNAL_HALT, false, 0,
                "Signal halt enabled but did not manifest this run (p=" + config.triggerProbability() + ")");
    }
}
