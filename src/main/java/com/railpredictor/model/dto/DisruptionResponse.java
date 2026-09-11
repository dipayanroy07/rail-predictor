package com.railpredictor.model.dto;

import com.railpredictor.model.enums.DisruptionType;

/**
 * One disruption that actually triggered during simulation. Simulated data - see
 * docs/architecture.md - {@code explanation} says why the model decided this, never presented as
 * a measured railway event.
 */
public record DisruptionResponse(DisruptionType disruptionType, int disruptionDelayMinutes, String explanation) {
}
