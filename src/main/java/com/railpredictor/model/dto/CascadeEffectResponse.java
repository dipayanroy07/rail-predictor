package com.railpredictor.model.dto;

/** One simulated secondary delay effect. Simulated data, not a measured railway event. */
public record CascadeEffectResponse(String description, int depth, int additionalDelayMinutes) {
}
