package com.railpredictor.prediction;

import com.railpredictor.config.TravelTimeProperties;
import com.railpredictor.model.domain.LiveTrainData;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Computes "base travel time": how long, from right now, until the train would arrive if nothing
 * further changes - remaining distance at the train's current speed. See
 * docs/prediction-model.md for exactly what this baseline does and doesn't already account for.
 *
 * <p>Negative distance/speed can't reach here - {@link LiveTrainData}'s own compact constructor
 * already rejects those at construction time (Phase 2), so only "missing" and "zero" need
 * handling here, not "negative".
 */
@Component
public class TravelTimeCalculator {

    private final TravelTimeProperties properties;

    public TravelTimeCalculator(TravelTimeProperties properties) {
        this.properties = properties;
    }

    public double baseTravelTimeMinutes(LiveTrainData train) {
        Objects.requireNonNull(train, "train");

        Double remainingDistanceKm = train.remainingDistanceKm();
        if (remainingDistanceKm == null || remainingDistanceKm <= 0) {
            // Unknown distance: nothing to compute from, so no base time to add.
            // Zero distance: the train has arrived, so correctly zero.
            return 0.0;
        }

        Double speedKmh = train.speedKmh();
        double effectiveSpeedKmh = (speedKmh == null || speedKmh <= 0)
                ? properties.assumedAverageSpeedKmhWhenUnknown()
                : speedKmh;

        return remainingDistanceKm / effectiveSpeedKmh * 60.0;
    }

    /** Whether {@link #baseTravelTimeMinutes} had to fall back to an assumed speed for this train. */
    public boolean usedAssumedSpeed(LiveTrainData train) {
        Objects.requireNonNull(train, "train");
        Double speedKmh = train.speedKmh();
        return speedKmh == null || speedKmh <= 0;
    }
}
