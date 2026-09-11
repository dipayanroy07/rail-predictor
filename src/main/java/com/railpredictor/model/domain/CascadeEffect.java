package com.railpredictor.model.domain;

/**
 * One simulated secondary effect produced by the cascade engine (e.g. a primary delay increasing
 * section occupancy and affecting a following train). {@code depth} is this effect's distance
 * from the primary delay in the cascade chain, used to enforce a maximum cascade depth.
 */
public record CascadeEffect(String description, int depth, int additionalDelayMinutes) {

    public CascadeEffect {
        description = Guard.requireNonBlank(description, "description");
        Guard.requireNonNegative(depth, "depth");
        Guard.requireNonNegative(additionalDelayMinutes, "additionalDelayMinutes");
    }
}
