package com.railpredictor.model.domain;

/**
 * How {@code PredictionResult.historicalAdjustmentMinutes} was actually resolved (Phase 16H-3) -
 * the smallest domain object that lets a consumer answer all three of: was historical data used
 * at all, which strategy was used, and where that data came from. Deliberately just these two
 * fields, not a larger "prediction metadata" object - nothing else about a prediction needed this
 * same "source vs. provenance" distinction.
 *
 * <p><b>{@code source} vs. {@code provenance} - two different concepts, never conflated:</b>
 * {@code source} ({@link HistoricalAdjustmentSource}) is <em>which calculation strategy won</em>
 * (section-level vs. station-level fallback vs. none); {@code provenance} is <em>where the
 * winning strategy's underlying data actually came from</em> (real RailRadar data, a mock
 * provider, unavailable, or an explicit {@code mixed(...)} combination - see
 * {@link DataProvenance}). A {@code SECTION}-sourced adjustment can have {@code MOCK} provenance
 * (if the mock section provider is active) exactly as easily as a {@code STATION_FALLBACK}
 * adjustment can - the two axes vary independently, and both must remain visible.
 *
 * <p>{@code provenance} is never invented: when {@code source} is {@code SECTION}, it is exactly
 * the {@code RemainingRouteHistoricalSummary}'s own {@code provenance()}; when
 * {@code STATION_FALLBACK}, it is exactly the station-level {@code HistoricalDelay}'s own
 * {@code source()}; when {@code NONE}, it is {@link DataProvenance#UNAVAILABLE} - the existing,
 * already-established vocabulary, never a new one.
 */
public record HistoricalAdjustmentResolution(HistoricalAdjustmentSource source, String provenance) {

    public HistoricalAdjustmentResolution {
        source = Guard.requireNonNull(source, "source");
        provenance = Guard.requireNonBlank(provenance, "provenance");
    }
}
