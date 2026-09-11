package com.railpredictor.model.domain;

import java.time.Instant;

/**
 * A compact, honest inventory of what real evaluation data actually exists (Phase 20) - the first
 * thing to check before trusting any accuracy number, per this phase's own instructions. Every
 * count here is a real database count (or {@code 0} when no repository is configured) - never
 * inferred, estimated, or padded with synthetic/mock data.
 *
 * <p>{@code pointInTimeReproducibilityNote} is qualitative, not a count, because the answer
 * genuinely differs by input: station/section historical profiles can be reconstructed with a
 * cutoff (Phase 16H-7/16F), but weather and railway-disruption data have no historical archive at
 * all - see docs/historical-data-design.md's and docs/prediction-model.md's own Phase 20 notes for
 * the full accounting.
 */
public record DataQualityReport(
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

    public DataQualityReport {
        Guard.requireNonNegative(totalSnapshots, "totalSnapshots");
        Guard.requireNonNegative(evaluatedSnapshots, "evaluatedSnapshots");
        Guard.requireNonNegative(exactEvaluations, "exactEvaluations");
        Guard.requireNonNegative(approximateEvaluations, "approximateEvaluations");
        Guard.requireNonNegative(pendingSnapshots, "pendingSnapshots");
        Guard.requireNonNegative(notEvaluableSnapshots, "notEvaluableSnapshots");
        Guard.requireNonNegative(weatherAvailableCount, "weatherAvailableCount");
        Guard.requireNonNegative(historicalAvailableCount, "historicalAvailableCount");
        Guard.requireNonNegative(disruptionImpactAvailableCount, "disruptionImpactAvailableCount");
        Guard.requireNonNegative(simulationContributedCount, "simulationContributedCount");
        Guard.requireNonNegative(distinctTrainCount, "distinctTrainCount");
        Guard.requireNonNegative(distinctStationCount, "distinctStationCount");
        pointInTimeReproducibilityNote = Guard.requireNonBlank(pointInTimeReproducibilityNote, "pointInTimeReproducibilityNote");
        if (exactEvaluations + approximateEvaluations != evaluatedSnapshots) {
            throw new IllegalArgumentException(
                    "exactEvaluations + approximateEvaluations must equal evaluatedSnapshots");
        }
    }

    /** The zero-data state - every count {@code 0}, an explicit note explaining there is nothing
     * to report on yet (no repository configured, or genuinely no snapshots exist). */
    public static DataQualityReport empty(String note) {
        return new DataQualityReport(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, null, note);
    }
}
