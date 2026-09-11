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
 * Simulates a high-congestion delay. There is no real occupancy/traffic feed to corroborate this
 * against yet (see {@code RouteProvider}), so - unlike {@link HeavyRainModel}/{@link
 * DenseFogModel} - this is purely {@code context.highCongestionEnabled()} as the master switch,
 * gated by a configured trigger probability, with delay magnitude drawn from a configured range.
 */
@Component
public class CongestionModel implements DisruptionModel {

    private final ProbabilityDelayConfig config;

    public CongestionModel(DisruptionProperties properties) {
        this.config = properties.highCongestion();
    }

    @Override
    public DisruptionResult simulate(LiveTrainData train, RouteSection section, SimulationContext context) {
        Objects.requireNonNull(train, "train");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(context, "context");

        if (!context.highCongestionEnabled()) {
            return new DisruptionResult(DisruptionType.HIGH_CONGESTION, false, 0,
                    "High congestion disabled for this simulation run");
        }

        Random random = DisruptionRandom.forModel(context.randomSeed(), DisruptionType.HIGH_CONGESTION);
        ProbabilisticDisruption.Outcome outcome = ProbabilisticDisruption.roll(
                true, config.triggerProbability(), config.minDelayMinutes(), config.maxDelayMinutes(), random);
        if (outcome.triggered()) {
            return new DisruptionResult(DisruptionType.HIGH_CONGESTION, true, outcome.delayMinutes(),
                    "Simulated high congestion (p=" + config.triggerProbability() + ") added " + outcome.delayMinutes() + " min");
        }
        return new DisruptionResult(DisruptionType.HIGH_CONGESTION, false, 0,
                "High congestion enabled but did not manifest this run (p=" + config.triggerProbability() + ")");
    }
}
