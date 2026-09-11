package com.railpredictor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The fixed delay-<b>change</b> statistics {@code MockSectionHistoricalDelayProvider} returns for
 * every train/section, until real section historical data is wired in for prediction. Unlike
 * {@link HistoricalMockProperties}, {@code averageDelayChangeMinutes}/{@code medianDelayChangeMinutes}
 * are deliberately <b>not</b> constrained to be non-negative - a delay change can genuinely be
 * negative (recovery), and a mock provider that couldn't represent that would misrepresent the
 * real statistic's shape. Defaulting {@code sampleCount} to 0 means "no historical data" by
 * default, matching every other mock provider's convention.
 */
@ConfigurationProperties(prefix = "historical.section.mock")
public record HistoricalSectionMockProperties(
        double averageDelayChangeMinutes,
        double medianDelayChangeMinutes,
        double standardDeviationMinutes,
        int sampleCount) {

    public HistoricalSectionMockProperties {
        if (standardDeviationMinutes < 0) {
            throw new IllegalArgumentException(
                    "historical.section.mock.standard-deviation-minutes must not be negative: " + standardDeviationMinutes);
        }
        if (sampleCount < 0) {
            throw new IllegalArgumentException(
                    "historical.section.mock.sample-count must not be negative: " + sampleCount);
        }
    }
}
