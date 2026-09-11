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
 * Simulates a planned-engineering-work delay. No real maintenance schedule feed exists yet, so
 * this is purely {@code context.engineeringBlockEnabled()} as the master switch, gated by a
 * configured trigger probability, with delay magnitude drawn from a configured range.
 */
@Component
public class EngineeringBlockModel implements DisruptionModel {

    private final ProbabilityDelayConfig config;

    public EngineeringBlockModel(DisruptionProperties properties) {
        this.config = properties.engineeringBlock();
    }

    @Override
    public DisruptionResult simulate(LiveTrainData train, RouteSection section, SimulationContext context) {
        Objects.requireNonNull(train, "train");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(context, "context");

        if (!context.engineeringBlockEnabled()) {
            return new DisruptionResult(DisruptionType.ENGINEERING_BLOCK, false, 0,
                    "Engineering block disabled for this simulation run");
        }

        Random random = DisruptionRandom.forModel(context.randomSeed(), DisruptionType.ENGINEERING_BLOCK);
        ProbabilisticDisruption.Outcome outcome = ProbabilisticDisruption.roll(
                true, config.triggerProbability(), config.minDelayMinutes(), config.maxDelayMinutes(), random);
        if (outcome.triggered()) {
            return new DisruptionResult(DisruptionType.ENGINEERING_BLOCK, true, outcome.delayMinutes(),
                    "Simulated engineering block (p=" + config.triggerProbability() + ") added " + outcome.delayMinutes() + " min");
        }
        return new DisruptionResult(DisruptionType.ENGINEERING_BLOCK, false, 0,
                "Engineering block enabled but did not manifest this run (p=" + config.triggerProbability() + ")");
    }
}
