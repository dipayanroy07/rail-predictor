package com.railpredictor.config;

/**
 * Shared shape for a probability-driven disruption model: a chance of triggering, and a delay
 * range to draw from when it does. Assumed values, not empirically validated - see
 * docs/architecture.md.
 */
public record ProbabilityDelayConfig(double triggerProbability, int minDelayMinutes, int maxDelayMinutes) {

    public ProbabilityDelayConfig {
        if (triggerProbability < 0 || triggerProbability > 1) {
            throw new IllegalArgumentException("triggerProbability must be between 0 and 1: " + triggerProbability);
        }
        if (minDelayMinutes < 0) {
            throw new IllegalArgumentException("minDelayMinutes must not be negative: " + minDelayMinutes);
        }
        if (maxDelayMinutes < minDelayMinutes) {
            throw new IllegalArgumentException(
                    "maxDelayMinutes (" + maxDelayMinutes + ") must be >= minDelayMinutes (" + minDelayMinutes + ")");
        }
    }
}
