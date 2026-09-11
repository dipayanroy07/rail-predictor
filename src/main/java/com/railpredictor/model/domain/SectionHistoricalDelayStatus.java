package com.railpredictor.model.domain;

/**
 * How a {@link SectionHistoricalDelayResult} should be treated by a future caller - deliberately
 * distinct from {@link HistoricalDelay}/{@link HistoricalDelayProfile}'s single "sampleCount 0
 * means no data" convention, since {@code HistoricalSectionDelayProvider}'s contract (Phase 16G)
 * must not collapse "never observed" and "observed but not enough samples yet" into one ambiguous
 * state.
 */
public enum SectionHistoricalDelayStatus {

    /** A profile exists and its sample count meets the configured minimum - safe to use. */
    AVAILABLE,

    /** A profile exists (the (fromStationCode, toStationCode) adjacency was genuinely observed at
     * least once) but its sample count is below the configured minimum - not yet trustworthy. */
    INSUFFICIENT_SAMPLES,

    /** No profile exists at all for this (trainNumber, fromStationCode, toStationCode) - the
     * adjacency has never been discovered in the raw data. */
    NOT_FOUND
}
