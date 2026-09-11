package com.railpredictor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configures how {@code HistoricalDelayCalculator} turns a historical average delay into a
 * prediction adjustment: {@code weight} replaces a blindly-hardcoded percentage, and
 * {@code minimumSampleCount} guards against trusting an average backed by too little data (below
 * it, the adjustment is 0 - see {@code HistoricalDelay}'s own "sampleCount 0 means no data"
 * convention). See docs/prediction-model.md.
 */
@ConfigurationProperties(prefix = "prediction.historical-adjustment")
public record HistoricalAdjustmentProperties(double weight, int minimumSampleCount) {

    public HistoricalAdjustmentProperties {
        if (weight < 0) {
            throw new IllegalArgumentException("prediction.historical-adjustment.weight must not be negative: " + weight);
        }
        if (minimumSampleCount < 0) {
            throw new IllegalArgumentException(
                    "prediction.historical-adjustment.minimum-sample-count must not be negative: " + minimumSampleCount);
        }
    }
}
