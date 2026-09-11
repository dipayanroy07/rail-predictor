package com.railpredictor.model.domain;

import java.util.List;

/**
 * The overall output of the simulation engine for one train: which disruptions fired, what
 * cascaded from them, and the resulting delay after recovery is applied.
 * {@code netDelayMinutes} is never negative, however large {@code recoveredDelayMinutes} gets.
 */
public record SimulationResult(
        List<DisruptionResult> disruptions,
        List<CascadeEffect> cascadeEffects,
        int directDisruptionDelayMinutes,
        int cascadeDelayMinutes,
        int recoveredDelayMinutes,
        int netDelayMinutes) {

    public SimulationResult {
        disruptions = List.copyOf(Guard.requireNonNull(disruptions, "disruptions"));
        cascadeEffects = List.copyOf(Guard.requireNonNull(cascadeEffects, "cascadeEffects"));
        Guard.requireNonNegative(directDisruptionDelayMinutes, "directDisruptionDelayMinutes");
        Guard.requireNonNegative(cascadeDelayMinutes, "cascadeDelayMinutes");
        Guard.requireNonNegative(recoveredDelayMinutes, "recoveredDelayMinutes");
        Guard.requireNonNegative(netDelayMinutes, "netDelayMinutes");
    }
}
