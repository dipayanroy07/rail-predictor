package com.railpredictor.simulation;

import com.railpredictor.config.DisruptionProperties;
import com.railpredictor.config.ProbabilityDelayConfig;
import com.railpredictor.model.domain.DisruptionResult;
import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.SimulationContext;
import com.railpredictor.model.domain.WeatherData;
import com.railpredictor.model.enums.DisruptionType;
import com.railpredictor.model.enums.WeatherCondition;
import java.util.Objects;
import java.util.Random;
import org.springframework.stereotype.Component;

/**
 * Simulates a dense-fog delay. Same structure as {@link HeavyRainModel}:
 * {@code context.denseFogEnabled()} is the master switch; an actual weather reading of
 * {@link WeatherCondition#DENSE_FOG} makes the disruption certain (only magnitude is randomised),
 * plain {@link WeatherCondition#FOG} is not treated as strong enough evidence of *dense* fog, so
 * it falls back to the probability roll like an unconfirmed "what if" scenario.
 */
@Component
public class DenseFogModel implements DisruptionModel {

    private final ProbabilityDelayConfig config;

    public DenseFogModel(DisruptionProperties properties) {
        this.config = properties.denseFog();
    }

    @Override
    public DisruptionResult simulate(LiveTrainData train, RouteSection section, SimulationContext context) {
        Objects.requireNonNull(train, "train");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(context, "context");

        if (!context.denseFogEnabled()) {
            return new DisruptionResult(DisruptionType.DENSE_FOG, false, 0,
                    "Dense fog disabled for this simulation run");
        }

        Random random = DisruptionRandom.forModel(context.randomSeed(), DisruptionType.DENSE_FOG);
        WeatherData weather = context.weather();
        if (weather != null && weather.condition() == WeatherCondition.DENSE_FOG) {
            int delay = ProbabilisticDisruption.randomDelayInRange(random, config.minDelayMinutes(), config.maxDelayMinutes());
            return new DisruptionResult(DisruptionType.DENSE_FOG, true, delay,
                    "Weather reading reports dense fog - simulated delay of " + delay + " min");
        }

        ProbabilisticDisruption.Outcome outcome = ProbabilisticDisruption.roll(
                true, config.triggerProbability(), config.minDelayMinutes(), config.maxDelayMinutes(), random);
        if (outcome.triggered()) {
            return new DisruptionResult(DisruptionType.DENSE_FOG, true, outcome.delayMinutes(),
                    "Simulated dense fog (p=" + config.triggerProbability() + ") added " + outcome.delayMinutes() + " min");
        }
        return new DisruptionResult(DisruptionType.DENSE_FOG, false, 0,
                "Dense fog enabled but did not manifest this run (p=" + config.triggerProbability() + ")");
    }
}
