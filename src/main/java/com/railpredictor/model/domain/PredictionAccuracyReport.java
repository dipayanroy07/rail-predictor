package com.railpredictor.model.domain;

import java.util.Map;

/**
 * The full accuracy report (Phase 16H-6) built by {@code PredictionAccuracyReportBuilder} over a
 * caller-supplied, already-filtered set of evaluated {@link PredictionSnapshot}s.
 *
 * <ul>
 *   <li>{@code overall} - every {@code EVALUATED_EXACT} and {@code EVALUATED_APPROXIMATE} snapshot
 *       combined (its own {@code exactCount}/{@code approximateCount} still expose the split).</li>
 *   <li>{@code exactOnly} - the same metrics computed using <em>only</em> {@code EVALUATED_EXACT}
 *       snapshots, so a caller can directly compare "combined" against "unambiguous matches only"
 *       rather than trusting a single blended number (Phase 16H-6 item 4's "expose both separate
 *       and combined" option).</li>
 *   <li>{@code bySource} - one {@link PredictionAccuracySlice} per {@link HistoricalAdjustmentSource}
 *       (always all three keys, even when a source has zero evaluated samples - a missing key
 *       would be indistinguishable from "not computed yet").</li>
 *   <li>{@code byProvenance} - one slice per distinct provenance string actually observed in the
 *       input (e.g. {@code "railradar"}, {@code "mock-provider"}, or a {@code "mixed(...)"}
 *       composite) - present only for provenances that actually occur, so mock-derived data is
 *       never silently folded into a real-data bucket.</li>
 *   <li>{@code byEvaluationMode} (Phase 16H-7) - one slice per {@link PredictionEvaluationMode},
 *       always both keys, even though only {@code LIVE_EVALUATION} is currently ever produced -
 *       so live-evaluation evidence and (once/if it ever exists) historical-backtest evidence can
 *       never be silently blended into one number without the split being visible.</li>
 * </ul>
 *
 * <p>No slice here ever ranks one source or provenance as "better" than another - that judgment
 * requires a sample-size/significance analysis this phase deliberately does not invent (see
 * docs/prediction-model.md's Phase 16H-6 notes).
 */
public record PredictionAccuracyReport(
        PredictionAccuracySlice overall,
        PredictionAccuracyComparison exactOnly,
        Map<HistoricalAdjustmentSource, PredictionAccuracySlice> bySource,
        Map<String, PredictionAccuracySlice> byProvenance,
        Map<PredictionEvaluationMode, PredictionAccuracySlice> byEvaluationMode) {

    public PredictionAccuracyReport {
        overall = Guard.requireNonNull(overall, "overall");
        exactOnly = Guard.requireNonNull(exactOnly, "exactOnly");
        bySource = Map.copyOf(Guard.requireNonNull(bySource, "bySource"));
        byProvenance = Map.copyOf(Guard.requireNonNull(byProvenance, "byProvenance"));
        byEvaluationMode = Map.copyOf(Guard.requireNonNull(byEvaluationMode, "byEvaluationMode"));
    }
}
