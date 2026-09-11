package com.railpredictor.model.dto;

import java.util.List;

/**
 * What the simulation actually found: only disruptions that triggered (not the full set of
 * models evaluated - see {@code PredictionOutputMapper}), any cascade effects, and how much delay
 * was recovered. All simulated data, not measured railway events.
 */
public record SimulationResponse(
        List<DisruptionResponse> triggeredDisruptions,
        List<CascadeEffectResponse> cascadeEffects,
        int recoveryMinutes) {
}
