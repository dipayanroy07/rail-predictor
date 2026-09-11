package com.railpredictor.model.domain;

import java.util.List;

/**
 * The per-section {@link SectionHistoricalDelayResult}s for a train's entire
 * {@link RemainingRoute}, plus a single defensible aggregate - see
 * {@code com.railpredictor.historical.RemainingRouteHistoricalAggregator}, the only thing that
 * builds this. Nothing in this type is wired into {@code PredictionEngine} yet (Phase 16H-1); it
 * exists purely as a queryable, testable prerequisite for whatever Phase 16H-2 decides to do with
 * it.
 *
 * <p><b>Aggregation formula</b>: {@code totalDelayChangeMinutes} is the plain signed <em>sum</em>
 * of {@code averageDelayChangeMinutes} over only the sections whose
 * {@link SectionHistoricalDelayResult#status()} is {@code AVAILABLE} - never an average, never
 * clamped to non-negative, never scaled by an arbitrary weight/percentage (that is a prediction-
 * layer decision, deliberately not made here - see docs/historical-data-design.md's Phase 16H-1
 * notes). Summing (not averaging) matches the statistic's own meaning: each section's delay
 * change is an independent, additive contribution to how much more (or less) delayed the train
 * becomes crossing it, so the total across several remaining sections is their sum.
 *
 * <p>{@code totalDelayChangeMinutes} is {@code null}, <b>not</b> {@code 0.0}, whenever
 * {@code availableSectionCount} is zero - a real, computed zero (historically stable) must never
 * be confused with "we have no idea" (see {@link RemainingRouteHistoricalStatus#NO_SECTIONS_AVAILABLE}/
 * {@link RemainingRouteHistoricalStatus#NO_REMAINING_SECTIONS}).
 *
 * <p>{@code provenance} is {@link DataProvenance#UNAVAILABLE} when {@code availableSectionCount}
 * is zero, otherwise {@code DataProvenance.combine(...)} over only the contributing (available)
 * sections' own sources - a mock or mixed source among the contributors is never hidden or
 * silently treated as equivalent to real data.
 */
public record RemainingRouteHistoricalSummary(
        String trainNumber,
        RouteCompleteness routeCompleteness,
        List<SectionHistoricalDelayResult> sectionResults,
        RemainingRouteHistoricalStatus status,
        int availableSectionCount,
        Double totalDelayChangeMinutes,
        String provenance) {

    public RemainingRouteHistoricalSummary {
        trainNumber = Guard.requireNonBlank(trainNumber, "trainNumber");
        routeCompleteness = Guard.requireNonNull(routeCompleteness, "routeCompleteness");
        sectionResults = List.copyOf(Guard.requireNonNull(sectionResults, "sectionResults"));
        status = Guard.requireNonNull(status, "status");
        Guard.requireNonNegative(availableSectionCount, "availableSectionCount");
        provenance = Guard.requireNonBlank(provenance, "provenance");
        if (availableSectionCount == 0 && totalDelayChangeMinutes != null) {
            throw new IllegalArgumentException("totalDelayChangeMinutes must be null when availableSectionCount is 0");
        }
        if (availableSectionCount > 0 && totalDelayChangeMinutes == null) {
            throw new IllegalArgumentException("totalDelayChangeMinutes must not be null when availableSectionCount > 0");
        }
    }
}
