package com.railpredictor.config;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for {@code ConfidenceCalculator}. */
@ConfigurationProperties(prefix = "prediction.confidence")
public record ConfidenceProperties(
        ConfidenceWeights weights, ConfidenceThresholds thresholds, int maxStableTriggeredDisruptions) {

    public ConfidenceProperties {
        Objects.requireNonNull(weights, "prediction.confidence.weights must be configured");
        Objects.requireNonNull(thresholds, "prediction.confidence.thresholds must be configured");
        if (maxStableTriggeredDisruptions < 0) {
            throw new IllegalArgumentException(
                    "prediction.confidence.max-stable-triggered-disruptions must not be negative: "
                            + maxStableTriggeredDisruptions);
        }
    }
}
