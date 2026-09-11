package com.railpredictor.model.domain;

/**
 * Which historical-data strategy actually produced {@code PredictionResult.historicalAdjustmentMinutes}
 * - Phase 16H-3's answer to "the same field can come from two fundamentally different paths, and
 * a warning string is not a reliable machine-readable contract." Deliberately separate from
 * {@link DataProvenance} (see {@link HistoricalAdjustmentResolution}'s own Javadoc for why the two
 * must never be conflated): this describes <em>which calculation strategy won</em>, not
 * <em>where the underlying data came from</em>.
 *
 * <p>Reflects the strategy actually selected for the final prediction - never reported merely
 * because a {@code RemainingRoute} happened to exist, or merely because station-level history was
 * queried. See {@code PredictionEngine} for exactly how each value is chosen.
 */
public enum HistoricalAdjustmentSource {

    /** The remaining-route section-level historical aggregate contributed the final adjustment -
     * whether every remaining section had usable history, or only some of them did. */
    SECTION,

    /** Section history did not provide a usable adjustment, so the station-level historical
     * adjustment was selected instead. */
    STATION_FALLBACK,

    /** Neither section history nor the station-level fallback contributed - the adjustment is 0
     * because there was nothing usable, not because the real figure happened to be zero. */
    NONE
}
