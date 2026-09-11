package com.railpredictor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configures {@code CascadeEngine}'s simulated secondary-delay chain: below
 * {@code minimumTriggerDelayMinutes} of primary delay, nothing cascades; otherwise each
 * successive affected entity is simulated to inherit {@code cascadeFactor} of the previous
 * entity's delay, bounded by {@code maxDepth}/{@code maxAffectedEntities} (number of entities) and
 * {@code maxTotalCascadeDelayMinutes} (summed delay). Assumed values, not empirically validated -
 * see docs/architecture.md.
 */
@ConfigurationProperties(prefix = "simulation.cascade")
public record CascadeProperties(
        int minimumTriggerDelayMinutes,
        double cascadeFactor,
        int maxDepth,
        int maxTotalCascadeDelayMinutes,
        int maxAffectedEntities) {

    public CascadeProperties {
        if (minimumTriggerDelayMinutes < 0) {
            throw new IllegalArgumentException(
                    "simulation.cascade.minimum-trigger-delay-minutes must not be negative: " + minimumTriggerDelayMinutes);
        }
        if (cascadeFactor < 0 || cascadeFactor > 1) {
            throw new IllegalArgumentException(
                    "simulation.cascade.cascade-factor must be between 0 and 1: " + cascadeFactor);
        }
        if (maxDepth < 1) {
            throw new IllegalArgumentException("simulation.cascade.max-depth must be at least 1: " + maxDepth);
        }
        if (maxTotalCascadeDelayMinutes < 0) {
            throw new IllegalArgumentException(
                    "simulation.cascade.max-total-cascade-delay-minutes must not be negative: " + maxTotalCascadeDelayMinutes);
        }
        if (maxAffectedEntities < 1) {
            throw new IllegalArgumentException(
                    "simulation.cascade.max-affected-entities must be at least 1: " + maxAffectedEntities);
        }
    }
}
