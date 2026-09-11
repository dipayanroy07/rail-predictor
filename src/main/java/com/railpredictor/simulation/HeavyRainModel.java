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
 * Simulates a heavy-rain delay. {@code context.heavyRainEnabled()} is the master switch: if off,
 * this never triggers, regardless of weather. If on and the train's actual weather reading
 * already reports {@link WeatherCondition#HEAVY_RAIN}, the disruption is treated as certain (only
 * its delay magnitude is randomised); otherwise it's a hypothetical "what if" scenario, triggered
 * with a configured probability. Delay magnitude is drawn from a configured range using a seed
 * derived from {@code context.randomSeed()}, so the same input always produces the same result.
 */
@Component
public class HeavyRainModel implements DisruptionModel {

    private final ProbabilityDelayConfig config;

    public HeavyRainModel(DisruptionProperties properties) {
        this.config = properties.heavyRain();
    }

    @Override
    public DisruptionResult simulate(LiveTrainData train, RouteSection section, SimulationContext context) {
        Objects.requireNonNull(train, "train");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(context, "context");

        if (!context.heavyRainEnabled()) {
            return new DisruptionResult(DisruptionType.HEAVY_RAIN, false, 0,
                    "Heavy rain disabled for this simulation run");
        }

        Random random = DisruptionRandom.forModel(context.randomSeed(), DisruptionType.HEAVY_RAIN);
        WeatherData weather = context.weather();
        if (weather != null && weather.condition() == WeatherCondition.HEAVY_RAIN) {
            int delay = ProbabilisticDisruption.randomDelayInRange(random, config.minDelayMinutes(), config.maxDelayMinutes());
            return new DisruptionResult(DisruptionType.HEAVY_RAIN, true, delay,
                    "Weather reading reports heavy rain - simulated delay of " + delay + " min");
        }

        ProbabilisticDisruption.Outcome outcome = ProbabilisticDisruption.roll(
                true, config.triggerProbability(), config.minDelayMinutes(), config.maxDelayMinutes(), random);
        if (outcome.triggered()) {
            return new DisruptionResult(DisruptionType.HEAVY_RAIN, true, outcome.delayMinutes(),
                    "Simulated heavy rain (p=" + config.triggerProbability() + ") added " + outcome.delayMinutes() + " min");
        }
        return new DisruptionResult(DisruptionType.HEAVY_RAIN, false, 0,
                "Heavy rain enabled but did not manifest this run (p=" + config.triggerProbability() + ")");
    }
}
