package com.railpredictor.model.dto;

import java.time.Instant;

/** JSON shape of {@code DataQualityReport} (Phase 20) - see that class for field meanings. */
public record DataQualityResponse(
        int totalSnapshots,
        int evaluatedSnapshots,
        int exactEvaluations,
        int approximateEvaluations,
        int pendingSnapshots,
        int notEvaluableSnapshots,
        int weatherAvailableCount,
        int historicalAvailableCount,
        int disruptionImpactAvailableCount,
        int simulationContributedCount,
        int distinctTrainCount,
        int distinctStationCount,
        Instant earliestPredictionMadeAt,
        Instant latestPredictionMadeAt,
        String pointInTimeReproducibilityNote) {
}
