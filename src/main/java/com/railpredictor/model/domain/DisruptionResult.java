package com.railpredictor.model.domain;

import com.railpredictor.model.enums.DisruptionType;

/**
 * The outcome of one {@code DisruptionModel} simulation. This is simulated data, not a measured
 * railway event - {@code description} should say why the model decided what it did.
 */
public record DisruptionResult(
        DisruptionType type,
        boolean triggered,
        int delayMinutes,
        String description) {

    public DisruptionResult {
        type = Guard.requireNonNull(type, "type");
        Guard.requireNonNegative(delayMinutes, "delayMinutes");
        description = Guard.requireNonBlank(description, "description");
    }
}
