package com.railpredictor.simulation;

import com.railpredictor.config.DisruptionProperties;
import com.railpredictor.config.SpeedRestrictionConfig;
import com.railpredictor.model.domain.DisruptionResult;
import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.SimulationContext;
import com.railpredictor.model.enums.DisruptionType;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Models a temporary speed restriction. Unlike the other disruption models, this one is
 * deterministic: {@code context.speedRestrictionKmh()} being present already means a restriction
 * is in effect, at that specific speed - there's nothing to randomly decide, so no {@link
 * java.util.Random} is used here.
 *
 * <p>When both the section's distance and the train's current speed are known (and the
 * restriction is actually slower than that speed), delay is computed as the extra time needed to
 * cover that distance at the restricted speed instead of the train's current speed. When either
 * is missing or the numbers don't make sense (e.g. zero distance), a configured flat delay is
 * used instead - an explicit assumption, not a measurement.
 */
@Component
public class SpeedRestrictionModel implements DisruptionModel {

    private final SpeedRestrictionConfig config;

    public SpeedRestrictionModel(DisruptionProperties properties) {
        this.config = properties.speedRestriction();
    }

    @Override
    public DisruptionResult simulate(LiveTrainData train, RouteSection section, SimulationContext context) {
        Objects.requireNonNull(train, "train");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(context, "context");

        Double restrictedSpeedKmh = context.speedRestrictionKmh();
        if (restrictedSpeedKmh == null) {
            return new DisruptionResult(DisruptionType.SPEED_RESTRICTION, false, 0,
                    "No speed restriction in effect for this simulation run");
        }

        Double normalSpeedKmh = train.speedKmh();
        Double distanceKm = section.distanceKm();
        if (restrictedSpeedKmh <= 0 || normalSpeedKmh == null || normalSpeedKmh <= restrictedSpeedKmh
                || distanceKm == null || distanceKm <= 0) {
            return new DisruptionResult(DisruptionType.SPEED_RESTRICTION, true, config.flatDelayMinutesWhenNotComputable(),
                    "Speed restriction to " + restrictedSpeedKmh + " km/h in effect, but delay could not be computed"
                            + " from available speed/distance data - using assumed flat delay of "
                            + config.flatDelayMinutesWhenNotComputable() + " min");
        }

        double normalTimeMinutes = distanceKm / normalSpeedKmh * 60.0;
        double restrictedTimeMinutes = distanceKm / restrictedSpeedKmh * 60.0;
        int delay = (int) Math.round(Math.max(0.0, restrictedTimeMinutes - normalTimeMinutes));
        return new DisruptionResult(DisruptionType.SPEED_RESTRICTION, true, delay,
                "Speed restriction to " + restrictedSpeedKmh + " km/h over " + distanceKm
                        + " km adds a computed " + delay + " min versus the train's current speed of "
                        + normalSpeedKmh + " km/h");
    }
}
