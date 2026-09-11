package com.railpredictor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Delay thresholds (in minutes) used by {@code DelayBasedSectionAnalyzer} to estimate a route
 * section's condition. These are engineering assumptions, not empirically validated figures -
 * see docs/architecture.md on assumed data.
 */
@ConfigurationProperties(prefix = "route.section-analysis")
public record SectionAnalysisProperties(int normalThresholdMinutes, int busyThresholdMinutes) {

    public SectionAnalysisProperties {
        if (normalThresholdMinutes < 0) {
            throw new IllegalArgumentException(
                    "route.section-analysis.normal-threshold-minutes must not be negative: " + normalThresholdMinutes);
        }
        if (busyThresholdMinutes < normalThresholdMinutes) {
            throw new IllegalArgumentException(
                    "route.section-analysis.busy-threshold-minutes (" + busyThresholdMinutes
                            + ") must be >= normal-threshold-minutes (" + normalThresholdMinutes + ")");
        }
    }
}
