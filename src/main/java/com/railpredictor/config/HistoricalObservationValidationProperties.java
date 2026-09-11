package com.railpredictor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Data-quality gates {@code HistoricalObservationMapper} applies before an observation is even
 * considered for persistence - see docs/historical-data-design.md's Phase 16C validation rules.
 * {@code maxPlausibleDelayMinutes} is an assumed sanity bound (Indian Railways trains are rarely,
 * if ever, delayed beyond a few days), not derived from real RailRadar data - a value outside
 * this range is treated as corrupt/nonsensical rather than a genuinely extreme delay.
 */
@ConfigurationProperties(prefix = "historical.observation-validation")
public record HistoricalObservationValidationProperties(int maxPlausibleDelayMinutes) {

    public HistoricalObservationValidationProperties {
        if (maxPlausibleDelayMinutes <= 0) {
            throw new IllegalArgumentException(
                    "historical.observation-validation.max-plausible-delay-minutes must be positive: "
                            + maxPlausibleDelayMinutes);
        }
    }
}
