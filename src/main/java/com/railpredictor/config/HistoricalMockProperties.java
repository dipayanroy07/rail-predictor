package com.railpredictor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The fixed historical delay statistics {@code MockHistoricalDelayProvider} returns for every
 * train/section, until real historical data is available (Phase 16). Defaulting
 * {@code sampleCount} to 0 means "no historical data" by default, matching
 * {@code HistoricalDelay}'s own convention - mock data, not a measurement.
 */
@ConfigurationProperties(prefix = "historical.mock")
public record HistoricalMockProperties(
        double averageDelayMinutes,
        double medianDelayMinutes,
        double standardDeviationMinutes,
        int sampleCount,
        String timePeriod) {

    public HistoricalMockProperties {
        if (averageDelayMinutes < 0) {
            throw new IllegalArgumentException("historical.mock.average-delay-minutes must not be negative: " + averageDelayMinutes);
        }
        if (medianDelayMinutes < 0) {
            throw new IllegalArgumentException("historical.mock.median-delay-minutes must not be negative: " + medianDelayMinutes);
        }
        if (standardDeviationMinutes < 0) {
            throw new IllegalArgumentException("historical.mock.standard-deviation-minutes must not be negative: " + standardDeviationMinutes);
        }
        if (sampleCount < 0) {
            throw new IllegalArgumentException("historical.mock.sample-count must not be negative: " + sampleCount);
        }
    }
}
