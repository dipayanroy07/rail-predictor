package com.railpredictor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configures {@code HistoricalSectionDelayProvider} implementations' minimum-sample policy -
 * deliberately a <b>separate</b> property from
 * {@code prediction.historical-adjustment.minimum-sample-count} (the station-level threshold),
 * per Phase 16G's explicit instruction not to silently reuse it.
 *
 * <p>Default ({@code 10}) is an <b>initial operational threshold, not a statistically proven
 * value</b>. It is chosen as double the station-level default (5) for one concrete, explainable
 * reason: a section's {@code delayChangeMinutes} is a <em>difference</em> of two independently
 * noisy quantities (a station's arrival delay, another station's departure delay), and the
 * variance of a difference of two independent variables is the <em>sum</em> of their variances -
 * so, for comparable per-quantity variance, a section-level average needs roughly twice as many
 * samples as a single station-level average to reach the same standard error. This is a reasoned
 * starting point, not a derived-from-real-data constant; it should be revisited once enough real
 * section observations exist to measure actual variance.
 */
@ConfigurationProperties(prefix = "historical.section")
public record HistoricalSectionProperties(int minimumSampleCount) {

    public HistoricalSectionProperties {
        if (minimumSampleCount < 0) {
            throw new IllegalArgumentException(
                    "historical.section.minimum-sample-count must not be negative: " + minimumSampleCount);
        }
    }
}
