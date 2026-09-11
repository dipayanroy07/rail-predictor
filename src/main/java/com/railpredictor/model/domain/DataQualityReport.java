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
 *
 * <p>{@code historicalObservationCount} (Phase 22) is the total row count of the entirely separate
 * {@code historical_observations} table - added so a caller can distinguish "no snapshot database
 * configured at all" / "database configured but genuinely nothing has ever been recorded" from
 * "real observations are being collected, but no prediction snapshots exist yet" (e.g. live
 * prediction requests aren't being made, or {@code prediction.evaluation.enabled=false}) - three
 * states that {@code totalSnapshots} alone could never tell apart, since it only ever counts the
 * (different) {@code prediction_snapshots} table.
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
        String pointInTimeReproducibilityNote,
        int historicalObservationCount) {

    /** Pre-Phase-22 shape, preserved so existing callers/tests need not change: defaults
     * {@code historicalObservationCount} to {@code 0} - correct for any caller not concerned with
     * distinguishing "no snapshots yet" from "no observations either". */
    public DataQualityReport(
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
        this(totalSnapshots, evaluatedSnapshots, exactEvaluations, approximateEvaluations, pendingSnapshots,
                notEvaluableSnapshots, weatherAvailableCount, historicalAvailableCount, disruptionImpactAvailableCount,
                simulationContributedCount, distinctTrainCount, distinctStationCount, earliestPredictionMadeAt,
                latestPredictionMadeAt, pointInTimeReproducibilityNote, 0);
    }

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
        Guard.requireNonNegative(historicalObservationCount, "historicalObservationCount");
        pointInTimeReproducibilityNote = Guard.requireNonBlank(pointInTimeReproducibilityNote, "pointInTimeReproducibilityNote");
        if (exactEvaluations + approximateEvaluations != evaluatedSnapshots) {
            throw new IllegalArgumentException(
                    "exactEvaluations + approximateEvaluations must equal evaluatedSnapshots");
        }
    }

    /** The zero-snapshot state - every count {@code 0}, an explicit note explaining there is
     * nothing to report on yet. Prefer {@link #empty(String, int)} whenever the real historical-
     * observation count is known, so a caller can tell "nothing at all" apart from "observations
     * exist, but no snapshots yet". */
    public static DataQualityReport empty(String note) {
        return empty(note, 0);
    }

    /** The zero-snapshot state with a real, separately-known historical-observation count - see
     * this record's own Javadoc for why {@code totalSnapshots} alone can't distinguish these
     * states. */
    public static DataQualityReport empty(String note, int historicalObservationCount) {
        return new DataQualityReport(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, null, note, historicalObservationCount);
    }
}
