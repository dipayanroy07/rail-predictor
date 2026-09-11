package com.railpredictor.railwaydisruption;

import com.railpredictor.model.domain.RailwayDisruption;
import com.railpredictor.model.domain.RailwayDisruptionStatus;
import java.time.Instant;
import java.util.Objects;

/**
 * Pure classification: where a {@link RailwayDisruption} stands relative to a reference instant
 * (Phase 18) - no provider/persistence knowledge, fully unit-testable in isolation, mirroring this
 * codebase's established split between pure calculation and provider/persistence orchestration
 * (e.g. {@code HistoricalDelayCalculator}, {@code PredictionOutcomeMatcher}).
 *
 * <p><b>An undated disruption ({@code effectiveFrom == null}) is always
 * {@link RailwayDisruptionStatus#UNKNOWN_VALIDITY}</b> - never assumed active, regardless of
 * whether {@code effectiveUntil} happens to be set (see this class's own tests for that exact
 * edge case).
 */
public final class RailwayDisruptionStatusClassifier {

    private RailwayDisruptionStatusClassifier() {
    }

    public static RailwayDisruptionStatus classify(RailwayDisruption disruption, Instant referenceInstant) {
        Objects.requireNonNull(disruption, "disruption");
        Objects.requireNonNull(referenceInstant, "referenceInstant");

        Instant effectiveFrom = disruption.effectiveFrom();
        if (effectiveFrom == null) {
            return RailwayDisruptionStatus.UNKNOWN_VALIDITY;
        }
        if (referenceInstant.isBefore(effectiveFrom)) {
            return RailwayDisruptionStatus.FUTURE_EFFECTIVE;
        }
        Instant effectiveUntil = disruption.effectiveUntil();
        if (effectiveUntil != null && referenceInstant.isAfter(effectiveUntil)) {
            return RailwayDisruptionStatus.EXPIRED;
        }
        return RailwayDisruptionStatus.ACTIVE;
    }
}
