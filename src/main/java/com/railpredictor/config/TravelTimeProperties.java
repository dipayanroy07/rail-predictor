package com.railpredictor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Fallback speed {@code TravelTimeCalculator} uses when a train's actual speed is unknown or
 * zero (distance/0 is undefined). An assumption, not a measurement - see docs/prediction-model.md.
 */
@ConfigurationProperties(prefix = "prediction.travel-time")
public record TravelTimeProperties(double assumedAverageSpeedKmhWhenUnknown) {

    public TravelTimeProperties {
        if (assumedAverageSpeedKmhWhenUnknown <= 0) {
            throw new IllegalArgumentException(
                    "prediction.travel-time.assumed-average-speed-kmh-when-unknown must be positive: "
                            + assumedAverageSpeedKmhWhenUnknown);
        }
    }
}
